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
    @Mock org.springframework.transaction.support.TransactionOperations recordTransactions;
    @InjectMocks ActionServiceImpl service;
    Task plan;
    SubTask step;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

    @BeforeEach void setup() {
        lenient().when(recordTransactions.execute(any())).thenAnswer(i -> ((org.springframework.transaction.support.TransactionCallback<?>) i.getArgument(0)).doInTransaction(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class)));
        lenient().when(tasks.findById(anyLong())).thenAnswer(i -> tasks.findLockedById(i.getArgument(0)));
        lenient().when(tasks.findAllById(any())).thenAnswer(i -> {
            java.util.List<Task> result = new java.util.ArrayList<>();
            for (Long id : (Iterable<Long>) i.getArgument(0)) tasks.findById(id).ifPresent(result::add);
            return result;
        });
        lenient().when(steps.findByTaskIdInOrderByTaskIdAscOrderNumAsc(anyCollection())).thenAnswer(i -> {
            java.util.List<SubTask> result = new java.util.ArrayList<>();
            for (Long id : (java.util.Collection<Long>) i.getArgument(0)) result.addAll(steps.findByTaskIdOrderByOrderNumAsc(id));
            return result;
        });
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
        request.setRawInput("练习编曲软件并编写节奏，同时完成英语听力练习30分钟"); request.setDurationMinutes(30);
        return request;
    }
    AIMatchResult.MatchDetail match(long id, String increment) {
        return AIMatchResult.MatchDetail.builder().subTaskId(id).increment(new BigDecimal(increment))
                .matchEvidence(id == 11 ? "练习编曲软件" : id == 12 ? "编写节奏" : "完成英语听力练习").build();
    }
    void analysis(AIMatchResult.MatchDetail... matches) {
        when(ai.analyzeAction(anyString(), eq("30分钟"), anyList())).thenReturn(AIMatchResult.builder()
                .matches(Arrays.asList(matches)).durationMinutes(30).aiAnalysis("已分析").build());
    }
    @Test void firstRecordCreatesAttendanceAndSecondOnlyUpdatesProgress() {
        eligible(); analysis(match(11, "0.20"));
        when(checkIns.existsByUserIdAndTaskIdAndCheckInDate(7L, 1L, today)).thenReturn(false, true);
        var first = service.syncAction(request());
        assertTrue(first.getUpdates().get(0).isCheckedInNow());
        assertEquals(1, first.getSavedRecordCount()); assertEquals(1, first.getNewCheckInCount()); assertEquals(30, first.getDurationMinutes());
        var second = service.syncAction(request());
        assertFalse(second.getUpdates().get(0).isCheckedInNow());
        assertEquals(1, second.getSavedRecordCount()); assertEquals(0, second.getNewCheckInCount()); assertEquals(30, second.getDurationMinutes());
        assertEquals(new BigDecimal("0.40"), step.getCurrentProgress());
        verify(checkIns, times(1)).saveAndFlush(any());
        verify(logs, times(2)).save(any());
    }
    @Test void snapshotsWeightedPlanGainSeparatelyFromStepGain() {
        eligible(); step.setWeight(new BigDecimal("0.25")); analysis(match(11, "0.20"));
        service.syncAction(request());
        ArgumentCaptor<ActionLog> saved = ArgumentCaptor.forClass(ActionLog.class);
        verify(logs).save(saved.capture());
        assertEquals(0, new BigDecimal("0.20").compareTo(saved.getValue().getContribution()));
        assertEquals(0, new BigDecimal("0.05").compareTo(saved.getValue().getPlanContribution()));
        step.setWeight(new BigDecimal("0.80"));
        assertEquals(0, new BigDecimal("0.05").compareTo(saved.getValue().getPlanContribution()));
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

    @Test void httpEvidenceCannotCompleteUnmentionedJunitAndMockMvcStep() {
        eligible();step.setContent("使用JUnit和MockMvc测试GET /hello并验证HTTP响应");
        var completed=match(11,"1");completed.setCompleted(true);
        completed.setCompletionEvidence("已完成GET /hello接口的HTTP访问并验证通过");analysis(completed);
        var input=request();input.setRawInput("已完成GET /hello接口的HTTP访问并验证通过");
        service.syncAction(input);
        assertTrue(step.getCurrentProgress().compareTo(new BigDecimal("0.30"))<=0);
    }

    @Test void explicitNotCompletedCannotBecomeOneHundredEvenWhenModelSaysCompleted() {
        eligible();var completed=match(11,"1");completed.setCompleted(true);
        completed.setCompletionEvidence("尚未完成编曲软件全部练习，正在学习基础操作");analysis(completed);
        var input=request();input.setRawInput(completed.getCompletionEvidence());
        service.syncAction(input);
        assertTrue(step.getCurrentProgress().compareTo(new BigDecimal("0.30"))<=0);
    }

    @Test void oneGenericCompletionSentenceCannotCompleteTwoDifferentSteps() {
        eligible();
        var second=SubTask.builder().id(12L).taskId(1L).content("编写节奏").weight(new BigDecimal("0.50")).currentProgress(BigDecimal.ZERO).build();
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(step,second));
        var a=match(11,"1");var b=match(12,"1");
        for(var m:List.of(a,b)){m.setCompleted(true);m.setCompletionEvidence("所有步骤的要求都已完成并验证通过");}
        analysis(a,b);
        var input=request();input.setRawInput("练习编曲软件；编写节奏；所有步骤的要求都已完成并验证通过");
        service.syncAction(input);
        assertTrue(step.getCurrentProgress().compareTo(new BigDecimal("0.30"))<=0);
        assertTrue(second.getCurrentProgress().compareTo(new BigDecimal("0.30"))<=0);
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
        SubTask second = SubTask.builder().id(12L).taskId(1L).content("练习节奏").weight(new BigDecimal("0.50"))
                .currentProgress(BigDecimal.ZERO).build();
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(step, second));
        analysis(match(11, "0.10"), match(12, "0.10"));
        var result = service.syncAction(request());
        assertEquals(2, result.getUpdates().size());
        assertEquals(2, result.getSavedRecordCount()); assertEquals(1, result.getNewCheckInCount()); assertEquals(30, result.getDurationMinutes());
        verify(checkIns).saveAndFlush(any());
        ArgumentCaptor<ActionLog> saved = ArgumentCaptor.forClass(ActionLog.class);
        verify(logs, times(2)).save(saved.capture());
        assertEquals(30, saved.getAllValues().stream().mapToInt(ActionLog::getDurationMinutes).sum());
    }
    @Test void broadDatabaseNoteCannotAdvanceEightStepsUsingSameEvidence() {
        eligible(false);
        List<SubTask> candidates = new ArrayList<>();
        List<AIMatchResult.MatchDetail> matches = new ArrayList<>();
        for (long id = 11; id < 19; id++) {
            candidates.add(SubTask.builder().id(id).taskId(1L).content("数据库步骤" + id)
                    .currentProgress(BigDecimal.ZERO).build());
            var match = match(id, "0.05"); match.setMatchEvidence("整理了关系型数据库的笔记"); matches.add(match);
        }
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(candidates);
        analysis(matches.toArray(AIMatchResult.MatchDetail[]::new));
        var request = request(); request.setRawInput("整理了关系型数据库的笔记");
        var result = service.syncAction(request);
        assertTrue(result.isMatchingAmbiguous());
        assertTrue(result.getUpdates().isEmpty());
        verifyNoInteractions(logs, checkIns, taskService);
    }
    @Test void inventedOrOverlappingMultiStepEvidenceIsRejected() {
        eligible(false);
        var second = SubTask.builder().id(12L).taskId(1L).content("编写节奏").weight(new BigDecimal("0.50")).currentProgress(BigDecimal.ZERO).build();
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(step, second));
        var firstMatch = match(11, "0.10"); firstMatch.setMatchEvidence("练习编曲软件并编写节奏");
        analysis(firstMatch, match(12, "0.10"));
        assertTrue(service.syncAction(request()).isMatchingAmbiguous());
        firstMatch.setMatchEvidence("不在用户原文中的学习事实");
        assertTrue(service.syncAction(request()).isMatchingAmbiguous());
        verifyNoInteractions(logs, checkIns, taskService);
    }
    @Test void undoLastEffectiveRecordRemovesAutomaticAttendance() {
        ActionLog action = ActionLog.builder().id(100L).userId(7L).taskId(1L).subTaskId(11L)
                .contribution(new BigDecimal("0.20")).createdAt(today.atTime(10, 0)).build();
        step.setCurrentProgress(new BigDecimal("0.20"));
        when(logs.findById(100L)).thenReturn(Optional.of(action));
        when(logs.existsById(100L)).thenReturn(true);
        when(steps.findById(11L)).thenReturn(Optional.of(step));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
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
        SubTask secondStep = SubTask.builder().id(22L).taskId(2L).content("听力练习").weight(BigDecimal.ONE)
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
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
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
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
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

    @Test void completedPlanCannotBeReopenedByUndoingLearningRecord() {
        plan.setStatus(PlanStatus.COMPLETED);
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
        when(logs.findById(100L)).thenReturn(Optional.of(ActionLog.builder().id(100L).userId(7L).taskId(1L).subTaskId(11L).build()));
        var error = assertThrows(BusinessException.class, () -> service.deleteAction(100L));
        assertEquals(400, error.getCode());
        assertEquals(PlanStatus.COMPLETED, plan.getStatus());
        verify(logs, never()).delete(any()); verifyNoInteractions(steps, taskService, checkIns);
    }

    @Test void historicalAnalysisCannotChangeCompletedPlan() {
        plan.setStatus(PlanStatus.COMPLETED);
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(plan));
        var request = request(); request.setTaskId(1L);
        assertThrows(BusinessException.class, () -> service.syncHistoricalAction(request, today.minusDays(1).atTime(15, 0), 30));
        verifyNoInteractions(ai, logs, steps, checkIns);
    }
}
