package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.CheckInCreateRequest;
import com.lifetrack.entity.CheckInRecord;
import com.lifetrack.entity.PlanStatus;
import com.lifetrack.entity.Task;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.CheckInRecordRepository;
import com.lifetrack.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {
    @Mock private CheckInRecordRepository checkInRecordRepository;
    @Mock private TaskRepository taskRepository;
    @InjectMocks private CheckInService checkInService;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRejectDuplicateCheckInBeforeSaving() {
        LocalDate today = LocalDate.now();
        Task task = activePlan(today.minusDays(3), today.plusDays(3));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(checkInRecordRepository.existsByUserIdAndTaskIdAndCheckInDate(7L, 1L, today)).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkInService.create(request(today)));
        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("重复打卡"));
    }

    @Test void oneClickAttendanceNeedsNoRemarkOrInventedDuration() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(activePlan(today.minusDays(1), today.plusDays(1))));
        when(checkInRecordRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var request = new CheckInCreateRequest(); request.setTaskId(1L);
        var response = checkInService.create(request);
        assertEquals("", response.getContent()); assertEquals(0, response.getDurationMinutes());
        assertEquals(today, response.getCheckInDate());
    }

    @Test
    void shouldRejectDateOutsidePlanPeriod() {
        LocalDate today = LocalDate.now();
        Task task = activePlan(today.minusDays(5), today.plusDays(5));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkInService.create(request(today.minusDays(6))));
        assertTrue(error.getMessage().contains("不在学习计划周期内"));
    }

    @Test
    void shouldRejectCheckInForCancelledPlan() {
        LocalDate today = LocalDate.now();
        Task task = activePlan(today.minusDays(5), today.plusDays(5));
        task.setStatus(PlanStatus.CANCELLED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkInService.create(request(today)));
        assertTrue(error.getMessage().contains("只有进行中的学习计划可以打卡"));
    }

    @Test
    void shouldCalculateCurrentAndLongestStreakFromRealDates() {
        LocalDate today = LocalDate.of(2026, 10, 11);
        SortedSet<LocalDate> dates = new TreeSet<>(List.of(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 3),
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 11)
        ));

        int[] streaks = CheckInService.calculateStreaks(dates, today);
        assertArrayEquals(new int[]{3, 3}, streaks);
    }

    @Test
    void shouldReturnZeroCurrentStreakAfterGap() {
        LocalDate today = LocalDate.of(2026, 10, 11);
        SortedSet<LocalDate> dates = new TreeSet<>(List.of(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6)
        ));
        assertArrayEquals(new int[]{0, 2}, CheckInService.calculateStreaks(dates, today));
    }

    @Test
    void shouldCalculateCompletionRateAndSevenDayTrend() {
        LocalDate today = LocalDate.now();
        Task task = activePlan(today.minusDays(10), today.plusDays(10));
        task.setTargetDays(5);
        List<CheckInRecord> records = List.of(
                record(today.minusDays(2), 30),
                record(today.minusDays(1), 45),
                record(today, 60)
        );
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(checkInRecordRepository.findByUserIdAndTaskIdOrderByCheckInDateAsc(7L, 1L))
                .thenReturn(records);

        var stats = checkInService.stats(1L);
        assertEquals(3, stats.getCheckedInDays());
        assertEquals(3, stats.getCurrentStreak());
        assertEquals(3, stats.getLongestStreak());
        assertEquals("60.0", stats.getCompletionRate().toPlainString());
        assertEquals(7, stats.getRecentSevenDays().size());
        assertTrue(stats.getRecentSevenDays().get(6).getCheckedIn());
        assertEquals(60, stats.getRecentSevenDays().get(6).getDurationMinutes());
    }

    @Test
    void shouldRejectFutureCheckInDate() {
        LocalDate today = LocalDate.now();
        Task task = activePlan(today.minusDays(1), today.plusDays(5));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkInService.create(request(today.plusDays(1))));

        assertEquals(400, error.getCode());
        verifyNoInteractions(checkInRecordRepository);
    }

    @Test
    void shouldTranslateDatabaseUniqueConstraintConflictToDuplicateCheckIn() {
        LocalDate today = LocalDate.now();
        Task task = activePlan(today.minusDays(1), today.plusDays(1));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(checkInRecordRepository.existsByUserIdAndTaskIdAndCheckInDate(7L, 1L, today))
                .thenReturn(false);
        when(checkInRecordRepository.saveAndFlush(any(CheckInRecord.class)))
                .thenThrow(new DataIntegrityViolationException("uk_check_in_user_task_date"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkInService.create(request(today)));

        assertEquals(409, error.getCode());
    }

    @Test
    void shouldRejectCheckInAndStatsForAnotherUsersPlan() {
        LocalDate today = LocalDate.now();
        Task task = activePlan(today.minusDays(1), today.plusDays(1));
        task.setUserId(99L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        BusinessException createError = assertThrows(BusinessException.class,
                () -> checkInService.create(request(today)));
        BusinessException statsError = assertThrows(BusinessException.class,
                () -> checkInService.stats(1L));

        assertEquals(403, createError.getCode());
        assertEquals(403, statsError.getCode());
        verifyNoInteractions(checkInRecordRepository);
    }

    @Test
    void shouldCapCompletionRateAtOneHundredPercent() {
        LocalDate today = LocalDate.now();
        Task task = activePlan(today.minusDays(3), today.plusDays(3));
        task.setTargetDays(2);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(checkInRecordRepository.findByUserIdAndTaskIdOrderByCheckInDateAsc(7L, 1L))
                .thenReturn(List.of(record(today.minusDays(2), 20), record(today.minusDays(1), 20), record(today, 20)));

        assertEquals("100", checkInService.stats(1L).getCompletionRate().toPlainString());
    }

    private CheckInCreateRequest request(LocalDate date) {
        CheckInCreateRequest request = new CheckInCreateRequest();
        request.setTaskId(1L);
        request.setCheckInDate(date);
        request.setContent("完成今天的学习内容");
        request.setDurationMinutes(60);
        return request;
    }

    private Task activePlan(LocalDate start, LocalDate end) {
        return Task.builder()
                .id(1L)
                .userId(7L)
                .title("测试计划")
                .startDate(start)
                .endDate(end)
                .targetDays(5)
                .status(PlanStatus.IN_PROGRESS)
                .build();
    }

    private CheckInRecord record(LocalDate date, int duration) {
        return CheckInRecord.builder()
                .userId(7L)
                .taskId(1L)
                .checkInDate(date)
                .content("学习")
                .durationMinutes(duration)
                .build();
    }
}
