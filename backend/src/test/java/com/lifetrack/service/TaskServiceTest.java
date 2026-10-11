package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.TaskUpdateRequest;
import com.lifetrack.entity.PlanStatus;
import com.lifetrack.entity.Task;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.ActionLogRepository;
import com.lifetrack.repository.SubTaskRepository;
import com.lifetrack.repository.TaskRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock private TaskRepository taskRepository;
    @Mock private SubTaskRepository subTaskRepository;
    @Mock private ActionLogRepository actionLogRepository;
    @Mock private AIService aiService;
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
