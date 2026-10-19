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
        when(aiService.generateMoodQuote(anyInt(), anyString())).thenReturn("保持节奏");

        var stats = dashboardService.getStats();
        assertEquals(2, stats.getCheckedInDays());
        assertEquals("50.0", stats.getCheckInCompletionRate().toPlainString());
        assertEquals(2, stats.getCurrentCheckInStreak());
        assertEquals(2, stats.getLongestCheckInStreak());
        assertEquals(7, stats.getCheckInTrend().size());
        assertEquals(1, stats.getCheckInTrend().get(6).getCheckInCount());
        assertEquals(60, stats.getCheckInTrend().get(6).getDurationMinutes());
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
        assertEquals("33.3", stats.getCheckInCompletionRate().toPlainString());
        assertEquals(1, stats.getCompletedPlanCount());
        assertEquals(1, stats.getTotalPlanCount());
    }
}
