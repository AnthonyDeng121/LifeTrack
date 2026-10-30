package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.*;
import com.lifetrack.entity.*;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeferredLearningRecordServiceTest {
    @Mock DeferredLearningRecordRepository records;
    @Mock TaskRepository tasks;
    @Mock CheckInRecordRepository checkIns;
    @Mock ActionLogRepository logs;
    @Mock CheckInService checkInService;
    @Mock ActionService actions;
    @Mock org.springframework.transaction.support.TransactionOperations recordTransactions;
    @InjectMocks DeferredLearningRecordService service;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    Task task;
    DeferredLearningRecord pending;
    @BeforeEach void setup() {
        lenient().when(recordTransactions.execute(any())).thenAnswer(i -> ((org.springframework.transaction.support.TransactionCallback<?>) i.getArgument(0)).doInTransaction(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class)));
        lenient().when(records.findById(anyLong())).thenAnswer(i -> records.findLockedById(i.getArgument(0)));
        lenient().when(tasks.findById(anyLong())).thenAnswer(i -> tasks.findLockedById(i.getArgument(0)));
        UserContext.setUserId(7L);
        task = Task.builder().id(1L).userId(7L).title("学习计划").status(PlanStatus.IN_PROGRESS)
                .startDate(today.minusDays(5)).endDate(today.plusDays(5)).build();
        pending = DeferredLearningRecord.builder().id(10L).userId(7L).taskId(1L).content("学习编曲")
                .durationMinutes(30).recordedAt(today.minusDays(1).atTime(14, 0)).status(DeferredLearningRecord.Status.PENDING).build();
    }
    @AfterEach void clear() { UserContext.clear(); }
    @Test void fallbackPersistsOriginalContentWithoutDuplicateCheckIn() {
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(task));
        when(records.save(any())).thenAnswer(i -> i.getArgument(0));
        var request = new DeferredLearningRecordRequest();
        request.setTaskId(1L); request.setContent("学习编曲"); request.setDurationMinutes(30);
        var response = service.save(request);
        assertEquals(DeferredLearningRecord.Status.PENDING, response.status());
        assertEquals("学习编曲", response.content());
        assertEquals(today, response.recordedAt().toLocalDate());
        assertEquals(0, response.recordedAt().getNano(), "与数据库秒级精度一致，禁止跨日四舍五入");
        verifyNoInteractions(checkInService, checkIns, actions);
    }
    void retryable() {
        when(records.findLockedById(10L)).thenReturn(Optional.of(pending));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(task));
    }
    @Test void retryUsesOriginalDateAndCannotApplyTwice() {
        retryable();
        when(actions.syncPreparedAction(any()))
                .thenReturn(ActionSyncResponse.builder().updates(List.of(ActionSyncResponse.TaskUpdateDetail.builder().build())).build());
        service.retry(10L);
        assertEquals(DeferredLearningRecord.Status.APPLIED, pending.getStatus());
        assertThrows(BusinessException.class, () -> service.retry(10L));
        verify(actions, times(1)).syncPreparedAction(any());
    }
    @Test void retryRetainsPendingWhenAIStillUnavailable() {
        retryable();
        when(actions.syncPreparedAction(any())).thenReturn(ActionSyncResponse.builder()
                .analysisUnavailable(true).updates(List.of()).aiAnalysis("暂不可用").build());
        service.retry(10L);
        assertEquals(DeferredLearningRecord.Status.PENDING, pending.getStatus());
        assertEquals("暂不可用", pending.getLastAnalysis());
    }
    @Test void unmatchedRetryRetainsRecordWithoutClaimingProgress() {
        retryable();
        when(actions.syncPreparedAction(any())).thenReturn(ActionSyncResponse.builder().updates(List.of()).build());
        service.retry(10L);
        assertEquals(DeferredLearningRecord.Status.UNMATCHED, pending.getStatus());
    }
    @Test void retryRejectsAnotherUsersRecord() {
        pending.setUserId(99L);
        when(records.findLockedById(10L)).thenReturn(Optional.of(pending));
        assertThrows(BusinessException.class, () -> service.retry(10L));
        verifyNoInteractions(actions, tasks);
    }
    @Test void completedPlanRejectsRetryWithoutConsumingPendingRecord() {
        retryable(); task.setStatus(PlanStatus.COMPLETED);
        assertThrows(BusinessException.class, () -> service.retry(10L));
        assertEquals(DeferredLearningRecord.Status.PENDING, pending.getStatus());
        verifyNoInteractions(actions); verify(records, never()).save(any());
    }
    @Test void expiredPlanAlsoRejectsRetryWithoutCallingAi() {
        retryable(); task.setStatus(PlanStatus.EXPIRED);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.retry(10L)).getCode());
        verifyNoInteractions(actions);
    }
    @Test void appliedRecordCannotBeEditedOrDeletedAsPending() {
        retryable(); pending.setStatus(DeferredLearningRecord.Status.APPLIED);
        when(records.findTaskIdById(10L)).thenReturn(Optional.of(1L));
        var edit = new DeferredLearningRecordUpdateRequest(); edit.setContent("不能重复计时"); edit.setDurationMinutes(30);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.update(10L, edit)).getCode());
        assertEquals(409, assertThrows(BusinessException.class, () -> service.delete(10L)).getCode());
        verify(records, never()).save(any()); verifyNoInteractions(actions);
    }
    @Test void importsLegacyCheckInUsingCheckInDateInsteadOfCreationDate() {
        CheckInRecord checkIn = CheckInRecord.builder().userId(7L).taskId(1L).content("旧版学习记录").durationMinutes(30)
                .checkInDate(today.minusDays(2)).createdAt(today.atTime(15, 0)).build();
        when(checkIns.findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(any(), any(), any())).thenReturn(List.of(checkIn));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(task));
        when(records.findByUserIdAndTaskId(7L, 1L)).thenReturn(List.of(), List.of(pending));
        assertEquals(1, service.importLegacyCheckIns());
        ArgumentCaptor<DeferredLearningRecord> saved = ArgumentCaptor.forClass(DeferredLearningRecord.class);
        verify(records).save(saved.capture());
        assertEquals(today.minusDays(2), saved.getValue().getRecordedAt().toLocalDate());
        pending.setRecordedAt(today.minusDays(2).atTime(12, 0));
        assertEquals(0, service.importLegacyCheckIns());
    }
}
