package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.entity.*;
import com.lifetrack.repository.*;
import com.lifetrack.service.impl.FeedbackServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {
    @Mock ActionLogRepository logs;
    @Mock UserProfileRepository profiles;
    @Mock TaskRepository tasks;
    @Mock CheckInRecordRepository checkIns;
    @Mock AIService ai;
    @InjectMocks FeedbackServiceImpl service;
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void reportIncludesEarlyCompletionAndActualAttendanceAsSeparateFacts() {
        UserContext.setUserId(7L);
        var plan = Task.builder().id(1L).title("Spring Boot").status(PlanStatus.COMPLETED)
                .targetDays(3).totalProgress(BigDecimal.ONE).build();
        when(tasks.findByUserId(7L)).thenReturn(List.of(plan));
        when(logs.findByUserIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(List.of());
        when(checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(7L, 1L)).thenReturn(List.of(CheckInRecord.builder().checkInDate(java.time.LocalDate.now()).build()));
        when(ai.generateWeeklyReport(eq(7L), anyList(), anyList())).thenReturn(com.lifetrack.dto.FeedbackReportResponse.builder().build());
        var report = service.getFeedbackReport();
        assertTrue(report.getPlanOutcomeSummary().contains("已完成 1 / 1 项"));
        assertTrue(report.getPlanOutcomeSummary().contains("累计打卡 1 天"));
        assertTrue(report.getPlanOutcomeSummary().contains("同日去重"));
        @SuppressWarnings("unchecked") ArgumentCaptor<List<Map<String, Object>>> context = ArgumentCaptor.forClass(List.class);
        verify(ai).generateWeeklyReport(eq(7L), eq(List.of()), context.capture());
        var row = context.getValue().get(0);
        assertEquals(PlanStatus.COMPLETED, row.get("status"));
        assertEquals(BigDecimal.ONE, row.get("learning_progress"));
        assertEquals(1, row.get("actual_check_in_days"));
        assertEquals(3, row.get("target_days"));
    }

    @Test void reportIgnoresCancelledLearningAndDeduplicatesDatesAcrossPlans() {
        UserContext.setUserId(7L);
        var first = Task.builder().id(1L).status(PlanStatus.COMPLETED).build();
        var second = Task.builder().id(2L).status(PlanStatus.IN_PROGRESS).build();
        var cancelled = Task.builder().id(3L).status(PlanStatus.CANCELLED).build();
        when(tasks.findByUserId(7L)).thenReturn(List.of(first, second, cancelled));
        var included = ActionLog.builder().taskId(1L).build();
        when(logs.findByUserIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(List.of(included, ActionLog.builder().taskId(3L).build()));
        var record = CheckInRecord.builder().checkInDate(java.time.LocalDate.now()).build();
        when(checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(eq(7L), anyLong())).thenReturn(List.of(record));
        when(ai.generateWeeklyReport(eq(7L), anyList(), anyList())).thenReturn(com.lifetrack.dto.FeedbackReportResponse.builder().build());
        var result = service.getFeedbackReport();
        assertTrue(result.getPlanOutcomeSummary().contains("已完成 1 / 2 项"));
        assertTrue(result.getPlanOutcomeSummary().contains("累计打卡 1 天"));
        verify(ai).generateWeeklyReport(eq(7L), eq(List.of(included)), argThat(rows -> rows.size() == 2));
        verify(checkIns, never()).findByUserIdAndTaskIdOrderByCheckInDateAsc(7L, 3L);
    }
}
