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
        when(checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(7L, 1L)).thenReturn(List.of(CheckInRecord.builder().build()));
        when(ai.generateWeeklyReport(eq(7L), anyList(), anyList())).thenReturn(com.lifetrack.dto.FeedbackReportResponse.builder().build());
        var report = service.getFeedbackReport();
        assertTrue(report.getPlanOutcomeSummary().contains("已完成 1 / 1 项"));
        assertTrue(report.getPlanOutcomeSummary().contains("累计打卡 1 次"));
        assertTrue(report.getPlanOutcomeSummary().contains("目标合计 3 天"));
        @SuppressWarnings("unchecked") ArgumentCaptor<List<Map<String, Object>>> context = ArgumentCaptor.forClass(List.class);
        verify(ai).generateWeeklyReport(eq(7L), eq(List.of()), context.capture());
        var row = context.getValue().get(0);
        assertEquals(PlanStatus.COMPLETED, row.get("status"));
        assertEquals(BigDecimal.ONE, row.get("learning_progress"));
        assertEquals(1, row.get("actual_check_in_days"));
        assertEquals(3, row.get("target_days"));
    }
}
