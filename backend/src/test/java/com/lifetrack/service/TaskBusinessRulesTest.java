package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.*;
import com.lifetrack.entity.*;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskBusinessRulesTest {
    @Mock TaskRepository tasks;
    @Mock SubTaskRepository steps;
    @Mock ActionLogRepository logs;
    @Mock AIService ai;
    @Mock CheckInRecordRepository checkIns;
    @Mock DeferredLearningRecordRepository deferred;
    @InjectMocks TaskService service;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    Task plan;
    SubTask step;

    @BeforeEach void setup() {
        UserContext.setUserId(7L);
        plan = Task.builder().id(1L).userId(7L).title("课程计划").category(Task.Category.学习)
                .status(PlanStatus.IN_PROGRESS).startDate(today.minusDays(2)).endDate(today.plusDays(7))
                .targetDays(5).totalProgress(BigDecimal.ZERO).build();
        step = SubTask.builder().id(11L).taskId(1L).content("练习").weight(BigDecimal.ONE)
                .currentProgress(BigDecimal.ZERO).isCompleted(0).build();
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    void locked() { when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan)); }
    TaskUpdateRequest shrink() {
        var request = new TaskUpdateRequest(); request.setStartDate(today); return request;
    }
    CheckInRecord attendance(long id, LocalDate date) {
        return CheckInRecord.builder().id(id).userId(7L).taskId(1L).checkInDate(date).build();
    }
    ManualTaskCreateRequest creation(BigDecimal... weights) {
        var request = new ManualTaskCreateRequest();
        request.setTitle("课程计划"); request.setCategory("学习"); request.setStartDate(today);
        request.setEndDate(today.plusDays(6)); request.setTargetDays(5);
        List<ManualTaskCreateRequest.SubTaskDTO> input = new ArrayList<>();
        for (var weight : weights) {
            var dto = new ManualTaskCreateRequest.SubTaskDTO(); dto.setContent("学习步骤"); dto.setWeight(weight); input.add(dto);
        }
        request.setSubTasks(input); return request;
    }

    @ParameterizedTest
    @EnumSource(value = PlanStatus.class, names = {"COMPLETED", "CANCELLED", "EXPIRED", "NOT_STARTED"})
    void cannotCompleteStepOutsideActivePlan(PlanStatus status) {
        plan.setStatus(status);
        if (status == PlanStatus.NOT_STARTED) { plan.setStartDate(today.plusDays(1)); }
        if (status == PlanStatus.EXPIRED) { plan.setEndDate(today.minusDays(1)); }
        locked(); when(steps.findById(11L)).thenReturn(Optional.of(step));
        assertThrows(BusinessException.class, () -> service.completeSubTask(11L));
        assertEquals(status, plan.getStatus());
        verify(steps, never()).save(any()); verify(tasks, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = PlanStatus.class, names = {"COMPLETED", "CANCELLED", "EXPIRED"})
    void terminalPlansRejectAllStepEdits(PlanStatus status) {
        plan.setStatus(status); locked(); when(steps.findById(11L)).thenReturn(Optional.of(step));
        var add = new SubTaskCreateRequest(); add.setContent("新步骤"); add.setWeight(new BigDecimal("0.3"));
        assertThrows(BusinessException.class, () -> service.addSubTask(1L, add));
        var edit = new SubTaskUpdateRequest(); edit.setContent("新内容");
        assertThrows(BusinessException.class, () -> service.updateSubTask(11L, edit));
        assertThrows(BusinessException.class, () -> service.deleteSubTask(11L));
        verify(steps, never()).save(any()); verify(steps, never()).saveAll(any()); verify(steps, never()).delete(any());
        assertEquals(status, plan.getStatus());
    }

    @Test void requiresConfirmationBeforePruningAttendance() {
        locked(); var outside = attendance(21L, today.minusDays(1));
        when(checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(7L, 1L)).thenReturn(List.of(outside));
        var error = assertThrows(BusinessException.class, () -> service.updateTask(1L, shrink()));
        assertEquals(409, error.getCode());
        assertEquals(today.minusDays(2), plan.getStartDate());
        verify(checkIns, never()).deleteAll(any()); verify(tasks, never()).save(any());
    }

    @Test void confirmationMustMatchExactAffectedRecords() {
        locked();
        when(checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(7L, 1L))
                .thenReturn(List.of(attendance(21L, today.minusDays(1)), attendance(22L, today.minusDays(2))));
        var request = shrink(); request.setDiscardOutsideCheckIns(true); request.setConfirmedDiscardCheckInIds(List.of(21L));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.updateTask(1L, request)).getCode());
        verify(checkIns, never()).deleteAll(any());
    }

    @Test void confirmedPeriodChangeDeletesOnlyOutsideRecordsAndKeepsProgress() {
        locked(); var outside = attendance(21L, today.minusDays(1)); var inside = attendance(22L, today);
        when(checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(7L, 1L)).thenReturn(List.of(outside, inside));
        when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
        plan.setTotalProgress(new BigDecimal("0.4"));
        var request = shrink(); request.setDiscardOutsideCheckIns(true); request.setConfirmedDiscardCheckInIds(List.of(21L));
        var result = service.updateTask(1L, request);
        assertEquals(today, result.getStartDate()); assertEquals(new BigDecimal("40.0"), result.getTotalProgress());
        verify(checkIns).deleteAll(List.of(outside)); verifyNoInteractions(logs, deferred);
    }

    @Test void expandedPeriodNeedsNoDestructiveConfirmation() {
        locked(); when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
        var request = new TaskUpdateRequest(); request.setEndDate(today.plusDays(9));
        assertEquals(today.plusDays(9), service.updateTask(1L, request).getEndDate());
    }

    @Test void contradictoryExplicitStatusIsRejectedBeforeDeletingRecords() {
        locked(); var request = shrink(); request.setStatus("NOT_STARTED");
        assertEquals(400, assertThrows(BusinessException.class, () -> service.updateTask(1L, request)).getCode());
        verifyNoInteractions(checkIns);
    }

    @Test void futurePeriodCannotBeSavedAsInProgress() {
        locked(); var request = new TaskUpdateRequest();
        request.setStartDate(today.plusDays(1)); request.setStatus("IN_PROGRESS");
        assertThrows(BusinessException.class, () -> service.updateTask(1L, request));
        verifyNoInteractions(checkIns);
    }

    @Test void changingPeriodWithoutExplicitStatusDerivesStableDateStatus() {
        locked(); when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
        var request = new TaskUpdateRequest(); request.setStartDate(today.plusDays(1));
        assertEquals("NOT_STARTED", service.updateTask(1L, request).getStatus());
        when(tasks.findById(1L)).thenReturn(Optional.of(plan));
        assertEquals("NOT_STARTED", service.getTaskDetail(1L).getStatus());
    }

    @Test void expiredPlanCannotBeReopenedByChangingDates() {
        locked(); plan.setStatus(PlanStatus.EXPIRED); plan.setEndDate(today.minusDays(1)); plan.setTargetDays(1);
        var request = new TaskUpdateRequest(); request.setEndDate(today.plusDays(7));
        assertThrows(BusinessException.class, () -> service.updateTask(1L, request));
    }

    @Test void completedPlanCannotChangePeriodOrTarget() {
        locked(); plan.setStatus(PlanStatus.COMPLETED);
        assertThrows(BusinessException.class, () -> service.updateTask(1L, shrink()));
        var request = new TaskUpdateRequest(); request.setTargetDays(4);
        assertThrows(BusinessException.class, () -> service.updateTask(1L, request));
        verifyNoInteractions(checkIns);
    }

    @Test void completedPlanMayRenameWithoutChangingFrozenBusinessData() {
        locked(); plan.setStatus(PlanStatus.COMPLETED); when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
        var request = new TaskUpdateRequest(); request.setTitle("修正标题"); request.setStatus("COMPLETED");
        var result = service.updateTask(1L, request);
        assertEquals("修正标题", result.getTitle()); assertEquals("COMPLETED", result.getStatus());
    }

    @Test void directProgressRecalculationCannotReopenOrCompleteTerminalPlan() {
        when(tasks.findById(1L)).thenReturn(Optional.of(plan));
        step.setCurrentProgress(BigDecimal.ONE); when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(step));
        plan.setStatus(PlanStatus.CANCELLED); service.updateTaskProgress(1L);
        assertEquals(PlanStatus.CANCELLED, plan.getStatus());
        plan.setStatus(PlanStatus.COMPLETED); service.updateTaskProgress(1L);
        assertEquals(PlanStatus.COMPLETED, plan.getStatus());
        plan.setStatus(PlanStatus.EXPIRED); service.updateTaskProgress(1L);
        assertEquals(PlanStatus.EXPIRED, plan.getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"2", "-1", "0", "0.00000001"})
    void creationRejectsInvalidWeightsBeforePersistingAnything(String value) {
        assertThrows(BusinessException.class, () -> service.createManualTask(creation(new BigDecimal(value), BigDecimal.ZERO)));
        verifyNoInteractions(tasks, steps);
    }

    @Test void creationRejectsInvalidTotalAndOversubscribedMissingWeights() {
        assertThrows(BusinessException.class, () -> service.createManualTask(creation(new BigDecimal("0.2"), new BigDecimal("0.2"))));
        assertThrows(BusinessException.class, () -> service.createManualTask(creation(BigDecimal.ONE, null)));
        verifyNoInteractions(tasks, steps);
    }

    @Test void omittedWeightsShareRemainderWithoutNegativeCorrection() {
        when(tasks.save(any())).thenAnswer(i -> { Task task = i.getArgument(0); task.setId(1L); return task; });
        var result = service.createManualTask(creation(new BigDecimal("0.6"), null, null));
        assertEquals(List.of(new BigDecimal("0.6000"), new BigDecimal("0.2000"), new BigDecimal("0.2000")),
                result.getSubTasks().stream().map(TaskDeconstructResponse.SubTaskDTO::getWeight).toList());
    }

    @Test void roundingToleranceProducesPositiveWeightsSummingExactlyToOne() {
        when(tasks.save(any())).thenAnswer(i -> { Task task = i.getArgument(0); task.setId(1L); return task; });
        var result = service.createManualTask(creation(new BigDecimal("0.3333"), new BigDecimal("0.3333"), new BigDecimal("0.3333")));
        assertEquals(0, result.getSubTasks().stream().map(TaskDeconstructResponse.SubTaskDTO::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE));
        assertTrue(result.getSubTasks().stream().allMatch(dto -> dto.getWeight().signum() > 0));
    }

    @Test void creatingPastPeriodImmediatelyMarksItExpired() {
        when(tasks.save(any())).thenAnswer(i -> { Task task = i.getArgument(0); task.setId(1L); return task; });
        var request = creation(BigDecimal.ONE); request.setStartDate(today.minusDays(7)); request.setEndDate(today.minusDays(1));
        service.createManualTask(request);
        var saved = ArgumentCaptor.forClass(Task.class); verify(tasks).save(saved.capture());
        assertEquals(PlanStatus.EXPIRED, saved.getValue().getStatus());
    }
}
