package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.*;
import com.lifetrack.entity.*;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.*;
import com.lifetrack.service.impl.ActionServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {
    @Mock ActionLogRepository logs;
    @Mock TaskRepository tasks;
    @Mock SubTaskRepository steps;
    @Mock TaskService taskService;
    @Mock AIService ai;
    @Mock CheckInRecordRepository checkIns;
    @Mock DeferredLearningRecordRepository deferredRecords;
    @InjectMocks ActionServiceImpl service;
    Task plan;
    SubTask step;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

    @BeforeEach void setup() {
        UserContext.setUserId(7L);
        plan = Task.builder().id(1L).userId(7L).title("编曲学习").category(Task.Category.学习)
                .status(PlanStatus.IN_PROGRESS).startDate(today.minusDays(1)).endDate(today.plusDays(2))
                .totalProgress(BigDecimal.ZERO).build();
        step = SubTask.builder().id(11L).taskId(1L).content("学习编曲软件").weight(BigDecimal.ONE)
                .currentProgress(BigDecimal.ZERO).isCompleted(0).build();
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    void eligible() {
        eligible(true);
    }
    void eligible(boolean updates) {
        when(tasks.findIdsByUserIdAndStatus(7L, PlanStatus.IN_PROGRESS)).thenReturn(List.of(1L));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(step));
        if (updates) doAnswer(i -> { plan.setTotalProgress(step.getCurrentProgress()); return null; })
                .when(taskService).updateTaskProgress(1L);
    }
    ActionSyncRequest request() {
        ActionSyncRequest request = new ActionSyncRequest();
        request.setRawInput("练习编曲软件30分钟"); request.setDurationInput("30分钟");
        return request;
    }
    AIMatchResult.MatchDetail match(long id, String increment) {
        return AIMatchResult.MatchDetail.builder().subTaskId(id).increment(new BigDecimal(increment)).build();
    }
    void analysis(AIMatchResult.MatchDetail... matches) {
        when(ai.analyzeAction(anyString(), eq("30分钟"), anyList())).thenReturn(AIMatchResult.builder()
                .matches(Arrays.asList(matches)).durationMinutes(30).aiAnalysis("已分析").build());
    }
    @Test void firstRecordCreatesAttendanceAndSecondOnlyUpdatesProgress() {
        eligible(); analysis(match(11, "0.20"));
        when(checkIns.existsByUserIdAndTaskIdAndCheckInDate(7L, 1L, today)).thenReturn(false, true);
        assertTrue(service.syncAction(request()).getUpdates().get(0).isCheckedInNow());
        assertFalse(service.syncAction(request()).getUpdates().get(0).isCheckedInNow());
        assertEquals(new BigDecimal("0.40"), step.getCurrentProgress());
        verify(checkIns, times(1)).saveAndFlush(any());
        verify(logs, times(2)).save(any());
    }
    @Test void unrelatedInputDoesNotSaveOrCheckIn() {
        when(tasks.findIdsByUserIdAndStatus(7L, PlanStatus.IN_PROGRESS)).thenReturn(List.of(1L));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(step));
        analysis();
        assertTrue(service.syncAction(request()).getUpdates().isEmpty());
        verifyNoInteractions(logs, checkIns, taskService);
    }
    @Test void clampsActualContributionAndIgnoresDuplicateAndForeignSteps() {
        eligible(); step.setCurrentProgress(new BigDecimal("0.95"));
        analysis(match(11, "0.30"), match(11, "0.20"), match(999, "0.30"));
        var result = service.syncAction(request());
        assertEquals(1, result.getUpdates().size(), "达到上限仍保留有效学习记录");
        assertEquals(0, result.getUpdates().get(0).getIncrement().signum());
        verify(logs).save(any());
        verify(checkIns).saveAndFlush(any());
        assertEquals(0, step.getCurrentProgress().compareTo(new BigDecimal("0.95")));
    }
    @Test void explicitCompletionUsesRemainingProgressAndStoresActualIncrement() {
        eligible(); step.setCurrentProgress(new BigDecimal("0.95"));
        var completed = match(11, "0.01");
        completed.setCompleted(true);
        completed.setCompletionEvidence("完成编曲软件全部操作练习并通过验收");
        analysis(completed);
        var request = request();
        request.setRawInput("今天完成编曲软件全部操作练习并通过验收");
        var result = service.syncAction(request);
        assertEquals(0, result.getUpdates().get(0).getIncrement().compareTo(new BigDecimal("5")));
        ArgumentCaptor<ActionLog> saved = ArgumentCaptor.forClass(ActionLog.class);
        verify(logs).save(saved.capture());
        assertEquals(new BigDecimal("0.05"), saved.getValue().getContribution());
        assertEquals(0, step.getCurrentProgress().compareTo(BigDecimal.ONE));
    }
    @Test void inventedCompletionEvidenceCannotPromoteToOneHundred() {
        eligible(); step.setCurrentProgress(new BigDecimal("0.95"));
        var completed = match(11, "0.01"); completed.setCompleted(true);
        completed.setCompletionEvidence("模型杜撰的完成证明不在用户输入中");
        analysis(completed);
        assertEquals(0, service.syncAction(request()).getUpdates().get(0).getIncrement().signum());
        assertEquals(0, step.getCurrentProgress().compareTo(new BigDecimal("0.95")));
    }
    @Test void rejectsOutsidePeriodBeforeCallingAI() {
        plan.setEndDate(today.minusDays(1));
        when(tasks.findIdsByUserIdAndStatus(7L, PlanStatus.IN_PROGRESS)).thenReturn(List.of(1L));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
        assertThrows(BusinessException.class, () -> service.syncAction(request()));
        verifyNoInteractions(ai, logs, checkIns);
    }
    @Test void rejectsUnownedSelectedPlan() {
        when(tasks.findIdsByUserIdAndStatus(7L, PlanStatus.IN_PROGRESS)).thenReturn(List.of());
        var request = request(); request.setTaskId(999L);
        assertThrows(BusinessException.class, () -> service.syncAction(request));
        verifyNoInteractions(ai, logs, checkIns);
    }
    @Test void multipleStepsCreateOneAttendanceAndDoNotDuplicateDuration() {
        eligible();
        SubTask second = SubTask.builder().id(12L).taskId(1L).content("练习节奏")
                .currentProgress(BigDecimal.ZERO).build();
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(step, second));
        analysis(match(11, "0.10"), match(12, "0.10"));
        assertEquals(2, service.syncAction(request()).getUpdates().size());
        verify(checkIns).saveAndFlush(any());
        ArgumentCaptor<ActionLog> saved = ArgumentCaptor.forClass(ActionLog.class);
        verify(logs, times(2)).save(saved.capture());
        assertEquals(30, saved.getAllValues().stream().mapToInt(ActionLog::getDurationMinutes).sum());
    }
    @Test void undoLastEffectiveRecordRemovesAutomaticAttendance() {
        ActionLog action = ActionLog.builder().id(100L).userId(7L).taskId(1L).subTaskId(11L)
                .contribution(new BigDecimal("0.20")).createdAt(today.atTime(10, 0)).build();
        step.setCurrentProgress(new BigDecimal("0.20"));
        when(logs.findById(100L)).thenReturn(Optional.of(action));
        when(logs.existsById(100L)).thenReturn(true);
        when(steps.findById(11L)).thenReturn(Optional.of(step));
        when(tasks.findById(1L)).thenReturn(Optional.of(plan));
        when(logs.findByTaskId(1L)).thenReturn(List.of());
        CheckInRecord record = CheckInRecord.builder().automatic(true).build();
        when(checkIns.findByUserIdAndTaskIdAndCheckInDate(7L, 1L, today)).thenReturn(Optional.of(record));
        service.deleteAction(100L);
        assertEquals(0, step.getCurrentProgress().signum());
        verify(checkIns).delete(record);
    }

    @Test void zeroIncrementLearningAtEstimateLimitStillKeepsAttendanceAndDuration() {
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
        var record = CheckInRecord.builder().automatic(true).build();
        when(checkIns.findByUserIdAndTaskIdAndCheckInDate(7L, 1L, today)).thenReturn(Optional.of(record));
        when(logs.findByTaskId(1L)).thenReturn(List.of(ActionLog.builder().userId(7L).taskId(1L)
                .contribution(BigDecimal.ZERO).durationMinutes(30).rawInput("继续练习编曲软件")
                .createdAt(today.atTime(10, 0)).build()));
        service.refreshAttendance(1L, today);
        assertEquals(30, record.getDurationMinutes());
        verify(checkIns).save(record);
        verify(checkIns, never()).delete(any());
    }

    @Test void rejectsNegativeAndZeroContributions() {
        when(tasks.findIdsByUserIdAndStatus(7L, PlanStatus.IN_PROGRESS)).thenReturn(List.of(1L));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(step));
        analysis(match(11, "-0.10"), match(11, "0"));
        assertTrue(service.syncAction(request()).getUpdates().isEmpty());
        verifyNoInteractions(logs, checkIns, taskService);
    }

    @Test void oneInputCanCheckInTwoPlansWithoutDuplicatingDuration() {
        eligible();
        Task secondPlan = Task.builder().id(2L).userId(7L).title("英语学习").category(Task.Category.学习)
                .status(PlanStatus.IN_PROGRESS).startDate(today).endDate(today.plusDays(1))
                .totalProgress(BigDecimal.ZERO).build();
        SubTask secondStep = SubTask.builder().id(22L).taskId(2L).content("听力练习")
                .currentProgress(BigDecimal.ZERO).build();
        when(tasks.findIdsByUserIdAndStatus(7L, PlanStatus.IN_PROGRESS)).thenReturn(List.of(1L, 2L));
        when(tasks.findLockedById(2L)).thenReturn(Optional.of(secondPlan));
        when(steps.findByTaskIdOrderByOrderNumAsc(2L)).thenReturn(List.of(secondStep));
        analysis(match(11, "0.10"), match(22, "0.20"));
        var updates = service.syncAction(request()).getUpdates();
        assertEquals(2, updates.size());
        assertTrue(updates.stream().allMatch(ActionSyncResponse.TaskUpdateDetail::isCheckedInNow));
        verify(checkIns, times(2)).saveAndFlush(any());
        ArgumentCaptor<ActionLog> saved = ArgumentCaptor.forClass(ActionLog.class);
        verify(logs, times(2)).save(saved.capture());
        assertEquals(30, saved.getAllValues().stream().mapToInt(ActionLog::getDurationMinutes).sum());
    }

    @Test void undoDoesNotRemoveLegacyManualAttendance() {
        ActionLog action = ActionLog.builder().id(100L).userId(7L).taskId(1L)
                .contribution(new BigDecimal("0.20")).createdAt(today.atTime(10, 0)).build();
        when(logs.findById(100L)).thenReturn(Optional.of(action));
        when(logs.existsById(100L)).thenReturn(true);
        when(checkIns.findByUserIdAndTaskIdAndCheckInDate(7L, 1L, today))
                .thenReturn(Optional.of(CheckInRecord.builder().automatic(null).build()));
        service.deleteAction(100L);
        verify(checkIns, never()).delete(any());
    }

    @Test void rejectsSecondUndoAfterWaitingForPlanLock() {
        when(logs.findById(100L)).thenReturn(Optional.of(ActionLog.builder().id(100L).userId(7L).taskId(1L).build()));
        when(logs.existsById(100L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.deleteAction(100L));
        verify(logs, never()).delete(any());
        verifyNoInteractions(steps, taskService, checkIns);
    }

    @Test void historicalAnalysisUsesOriginalDateEvenAfterPlanExpired() {
        LocalDateTime original = today.minusDays(1).atTime(15, 0);
        plan.setStatus(PlanStatus.EXPIRED);
        plan.setEndDate(today.minusDays(1));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(step));
        analysis(match(11, "0.20"));
        var request = request(); request.setTaskId(1L);
        service.syncHistoricalAction(request, original, 30);
        ArgumentCaptor<ActionLog> saved = ArgumentCaptor.forClass(ActionLog.class);
        verify(logs).save(saved.capture());
        assertEquals(original, saved.getValue().getCreatedAt());
        assertEquals(30, saved.getValue().getDurationMinutes());
        verify(checkIns).existsByUserIdAndTaskIdAndCheckInDate(7L, 1L, original.toLocalDate());
        verify(tasks, never()).findIdsByUserIdAndStatus(any(), any());
    }
}
