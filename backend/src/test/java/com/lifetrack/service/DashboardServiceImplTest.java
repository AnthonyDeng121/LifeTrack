package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.entity.CheckInRecord;
import com.lifetrack.entity.PlanStatus;
import com.lifetrack.entity.Task;
import com.lifetrack.repository.*;
import com.lifetrack.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {
    @Mock private ActionLogRepository actionLogRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private SubTaskRepository subTaskRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private AIService aiService;
    @Mock private CheckInRecordRepository checkInRecordRepository;
    @InjectMocks private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(7L);
        UserContext.setUsername("测试用户");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldBuildDashboardMetricsFromPersistedCheckIns() {
        LocalDate today = LocalDate.now();
        Task plan = Task.builder()
                .id(1L).userId(7L).title("计划")
                .targetDays(4).status(PlanStatus.IN_PROGRESS)
                .build();
        List<CheckInRecord> records = List.of(
                record(today.minusDays(1), 30), record(today, 60)
        );

        when(taskRepository.findByUserId(7L)).thenReturn(List.of(plan));
        when(checkInRecordRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
                eq(7L), any(LocalDate.class), any(LocalDate.class))).thenReturn(records);
        when(actionLogRepository.findByUserIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(7L)).thenReturn(Optional.empty());

        var stats = dashboardService.getStats();
        assertEquals(2, stats.getCheckedInDays());
        assertEquals("0.0", stats.getLearningPlanCompletionRate().toPlainString());
        assertEquals(2, stats.getCurrentCheckInStreak());
        assertEquals(2, stats.getLongestCheckInStreak());
        assertEquals(7, stats.getCheckInTrend().size());
        assertEquals(1, stats.getCheckInTrend().get(6).getCheckInCount());
        assertEquals(60, stats.getCheckInTrend().get(6).getDurationMinutes());
        verifyNoInteractions(aiService);
    }

    @Test void manualEndRemainsInDenominatorButIsNotLearningCompletion() {
        when(taskRepository.findByUserId(7L)).thenReturn(List.of(
                Task.builder().id(1L).status(PlanStatus.ENDED).build(),
                Task.builder().id(2L).status(PlanStatus.COMPLETED).build()));
        var stats = dashboardService.getStats();
        assertEquals(1, stats.getCompletedPlanCount());
        assertEquals(2, stats.getTotalPlanCount());
        assertEquals(0, stats.getLearningPlanCompletionRate().compareTo(new java.math.BigDecimal("50")));
        verifyNoInteractions(aiService);
    }

    private CheckInRecord record(LocalDate date, int duration) {
        return CheckInRecord.builder()
                .userId(7L).taskId(1L).checkInDate(date)
                .content("学习").durationMinutes(duration)
                .build();
    }

    @Test
    void earlyCompletionCountsAsAchievementWithoutInflatingAttendance() {
        Task plan = Task.builder().id(1L).userId(7L).title("提前学完")
                .targetDays(3).status(PlanStatus.COMPLETED).build();
        when(taskRepository.findByUserId(7L)).thenReturn(List.of(plan));
        when(checkInRecordRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
                eq(7L), any(), any())).thenReturn(List.of(record(LocalDate.now(), 30)));
        when(userProfileRepository.findByUserId(7L)).thenReturn(Optional.empty());
        var stats = dashboardService.getStats();
        assertEquals("100.0", stats.getLearningPlanCompletionRate().toPlainString());
        assertEquals(1, stats.getCheckedInDays());
        assertEquals(1, stats.getCompletedPlanCount());
        assertEquals(1, stats.getTotalPlanCount());
    }

    @Test
    void deduplicatesNaturalDaysAndExcludesCancelledPlansFromEveryDashboardMetric() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        Task completed = Task.builder().id(1L).userId(7L).status(PlanStatus.COMPLETED).targetDays(1).build();
        Task active = Task.builder().id(2L).userId(7L).status(PlanStatus.IN_PROGRESS).targetDays(9).build();
        Task cancelled = Task.builder().id(3L).userId(7L).status(PlanStatus.CANCELLED).targetDays(2).build();
        when(taskRepository.findByUserId(7L)).thenReturn(List.of(completed, active, cancelled));
        var first = record(today, 30);
        var second = record(today, 20); second.setTaskId(2L);
        var excluded = record(today.minusDays(1), 100); excluded.setTaskId(3L);
        when(checkInRecordRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(eq(7L), any(), any()))
                .thenReturn(List.of(first, second, excluded));
        var includedLog = com.lifetrack.entity.ActionLog.builder().taskId(2L).category(Task.Category.学习)
                .createdAt(today.atTime(10, 0)).durationMinutes(10).contribution(new java.math.BigDecimal("0.2")).build();
        var cancelledLog = com.lifetrack.entity.ActionLog.builder().taskId(3L).category(Task.Category.学习)
                .createdAt(today.atTime(10, 0)).durationMinutes(100).contribution(java.math.BigDecimal.ONE).build();
        when(actionLogRepository.findByUserIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(List.of(includedLog, cancelledLog));
        var stats = dashboardService.getStats();
        assertEquals("50.0", stats.getLearningPlanCompletionRate().toPlainString());
        assertEquals(2, stats.getTotalPlanCount()); assertEquals(1, stats.getCancelledPlanCount());
        assertEquals(1, stats.getCheckedInDays()); assertEquals(1, stats.getCurrentCheckInStreak());
        assertEquals(1, stats.getLongestCheckInStreak());
        assertEquals(0, stats.getCheckInTrend().get(5).getCheckInCount());
        assertEquals(1, stats.getCheckInTrend().get(6).getCheckInCount());
        assertEquals(50, stats.getCheckInTrend().get(6).getDurationMinutes());
        assertEquals("20.0", stats.getTodayTotalProgress().toPlainString());
        assertEquals(10, stats.getTimeDistribution().get(0).getMinutes());
        assertEquals("20.0", stats.getWeeklyTrend().get(6).getProgress().toPlainString());
        org.mockito.Mockito.verify(checkInRecordRepository, org.mockito.Mockito.never()).deleteAll(anyIterable());
    }

    @Test void cancelledOnlyHasZeroDashboardButDoesNotDeleteHistory() {
        when(taskRepository.findByUserId(7L)).thenReturn(List.of(Task.builder().id(1L).userId(7L).status(PlanStatus.CANCELLED).build()));
        when(checkInRecordRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(eq(7L), any(), any()))
                .thenReturn(List.of(record(LocalDate.now(), 30)));
        var stats = dashboardService.getStats();
        assertEquals(0, stats.getCheckedInDays()); assertEquals(0, stats.getTotalPlanCount());
        assertEquals(1, stats.getCancelledPlanCount());
        assertEquals(0, stats.getLearningPlanCompletionRate().signum());
        assertEquals(0, stats.getTodayTotalProgress().signum());
        assertEquals(0, stats.getTimeDistribution().size());
    }
}
