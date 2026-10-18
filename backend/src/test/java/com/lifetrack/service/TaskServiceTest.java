package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.TaskUpdateRequest;
import com.lifetrack.dto.ManualTaskCreateRequest;
import com.lifetrack.entity.PlanStatus;
import com.lifetrack.entity.Task;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.ActionLogRepository;
import com.lifetrack.repository.SubTaskRepository;
import com.lifetrack.repository.TaskRepository;
import com.lifetrack.repository.CheckInRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock private TaskRepository taskRepository;
    @Mock private SubTaskRepository subTaskRepository;
    @Mock private ActionLogRepository actionLogRepository;
    @Mock private AIService aiService;
    @Mock private CheckInRecordRepository checkInRecordRepository;
    @Mock private com.lifetrack.repository.DeferredLearningRecordRepository deferredRecords;
    @InjectMocks private TaskService taskService;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void batchDeleteMustCheckAllOwnersBeforeDeleting() {
        Task owned = planOwnedByCurrentUser();
        Task foreign = planOwnedByCurrentUser();
        foreign.setId(2L);
        foreign.setUserId(99L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(owned));
        when(taskRepository.findById(2L)).thenReturn(Optional.of(foreign));
        assertThrows(BusinessException.class, () -> taskService.deleteTasks(List.of(1L, 2L)));
        verifyNoInteractions(checkInRecordRepository, subTaskRepository, actionLogRepository);
        verify(taskRepository, org.mockito.Mockito.never()).delete(any(Task.class));
    }

    @Test
    void batchDeleteMustRejectMissingTaskBeforeDeleting() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(planOwnedByCurrentUser()));
        when(taskRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> taskService.deleteTasks(List.of(1L, 2L)));
        verifyNoInteractions(checkInRecordRepository, subTaskRepository, actionLogRepository);
    }

    @Test
    void batchDeleteRejectsEmptySelection() {
        assertThrows(BusinessException.class, () -> taskService.deleteTasks(List.of()));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void shouldRejectTargetDaysBeyondPlanPeriod() {
        Task task = planOwnedByCurrentUser();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(LocalDate.of(2026, 10, 5));
        request.setTargetDays(6);

        BusinessException error = assertThrows(BusinessException.class,
                () -> taskService.updateTask(1L, request));
        assertEquals("目标打卡天数必须在1到计划周期天数之间", error.getMessage());
    }

    @Test
    void shouldRejectTransitionFromCompletedBackToInProgress() {
        Task task = planOwnedByCurrentUser();
        task.setStatus(PlanStatus.COMPLETED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setStatus("IN_PROGRESS");

        BusinessException error = assertThrows(BusinessException.class,
                () -> taskService.updateTask(1L, request));
        assertTrue(error.getMessage().contains("不能从已完成变更为进行中"));
    }

    @Test
    void shouldUpdateEditablePlanFields() {
        Task task = planOwnedByCurrentUser();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setTitle("Java 学习计划");
        request.setEndDate(LocalDate.now().plusDays(19));
        request.setTargetDays(15);

        var response = taskService.updateTask(1L, request);
        assertEquals("Java 学习计划", response.getTitle());
        assertEquals(15, response.getTargetDays());
    }

    @Test
    void shouldRejectUpdatingAnotherUsersPlan() {
        Task task = planOwnedByCurrentUser();
        task.setUserId(99L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setTitle("不能修改的计划");
        BusinessException error = assertThrows(BusinessException.class,
                () -> taskService.updateTask(1L, request));

        assertEquals(403, error.getCode());
        verifyNoInteractions(subTaskRepository, actionLogRepository, checkInRecordRepository);
    }

    @Test
    void shouldAllowTransitionFromInProgressToCompleted() {
        Task task = planOwnedByCurrentUser();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setStatus("COMPLETED");

        assertEquals("COMPLETED", taskService.updateTask(1L, request).getStatus());
    }

    @Test
    void shouldCreateManualPlanWithoutCallingAiService() {
        ManualTaskCreateRequest request = new ManualTaskCreateRequest();
        request.setTitle("手动学习计划");
        request.setCategory("学习");
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusDays(6));
        request.setTargetDays(5);
        ManualTaskCreateRequest.SubTaskDTO step = new ManualTaskCreateRequest.SubTaskDTO();
        step.setContent("完成第一章");
        request.setSubTasks(List.of(step));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        var response = taskService.createManualTask(request);

        assertEquals(12L, response.getTaskId());
        assertEquals(1, response.getSubTasks().size());
        verify(subTaskRepository).saveAll(any());
        verifyNoInteractions(aiService);
    }

    private Task planOwnedByCurrentUser() {
        return Task.builder()
                .id(1L)
                .userId(7L)
                .title("原计划")
                .category(Task.Category.学习)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(9))
                .targetDays(8)
                .status(PlanStatus.IN_PROGRESS)
                .totalProgress(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
