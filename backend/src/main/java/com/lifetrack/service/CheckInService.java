package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.CheckInCreateRequest;
import com.lifetrack.dto.CheckInResponse;
import com.lifetrack.dto.CheckInStatsResponse;
import com.lifetrack.entity.CheckInRecord;
import com.lifetrack.entity.PlanStatus;
import com.lifetrack.entity.Task;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.CheckInRecordRepository;
import com.lifetrack.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckInService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CheckInRecordRepository checkInRecordRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public CheckInResponse create(CheckInCreateRequest request) {
        Long userId = UserContext.getUserId();
        // 与自然语言记录共用计划行锁，避免手动接口和自动打卡竞争。
        taskRepository.findLockedById(request.getTaskId());
        Task task = getOwnedTask(request.getTaskId(), userId);
        LocalDate checkInDate = request.getCheckInDate() == null ? today() : request.getCheckInDate();
        validateCheckIn(task, checkInDate);

        if (checkInRecordRepository.existsByUserIdAndTaskIdAndCheckInDate(userId, task.getId(), checkInDate)) {
            throw new BusinessException(409, "该学习计划在所选日期已经打卡，请勿重复打卡");
        }

        CheckInRecord record = CheckInRecord.builder()
                .userId(userId)
                .taskId(task.getId())
                .checkInDate(checkInDate)
                .content(request.getContent() == null ? "" : request.getContent().trim())
                .durationMinutes(request.getDurationMinutes() == null ? 0 : request.getDurationMinutes())
                .mood(request.getMood() == null ? null : request.getMood().trim())
                .build();
        try {
            return toResponse(checkInRecordRepository.saveAndFlush(record), task.getTitle());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(409, "该学习计划当天已经打卡，请勿重复打卡");
        }
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> history(Long taskId, LocalDate startDate, LocalDate endDate) {
        Long userId = UserContext.getUserId();
        LocalDate queryStart = startDate == null ? LocalDate.of(1970, 1, 1) : startDate;
        LocalDate queryEnd = endDate == null ? today() : endDate;
        if (queryStart.isAfter(queryEnd)) {
            throw new BusinessException(400, "查询开始日期不能晚于结束日期");
        }

        List<CheckInRecord> records;
        Map<Long, String> taskTitles;
        if (taskId != null) {
            Task task = getOwnedTask(taskId, userId);
            records = checkInRecordRepository
                    .findByUserIdAndTaskIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
                            userId, taskId, queryStart, queryEnd);
            taskTitles = Map.of(taskId, task.getTitle());
        } else {
            records = checkInRecordRepository
                    .findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
                            userId, queryStart, queryEnd);
            Set<Long> taskIds = records.stream().map(CheckInRecord::getTaskId).collect(Collectors.toSet());
            taskTitles = taskRepository.findAllById(taskIds).stream()
                    .filter(task -> task.getUserId().equals(userId))
                    .collect(Collectors.toMap(Task::getId, Task::getTitle));
        }

        return records.stream()
                .map(record -> toResponse(record, taskTitles.getOrDefault(record.getTaskId(), "已删除的计划")))
                .toList();
    }

    @Transactional(readOnly = true)
    public CheckInStatsResponse stats(Long taskId) {
        Long userId = UserContext.getUserId();
        Task task = getOwnedTask(taskId, userId);
        List<CheckInRecord> records = checkInRecordRepository
                .findByUserIdAndTaskIdOrderByCheckInDateAsc(userId, taskId);

        SortedSet<LocalDate> dates = records.stream()
                .map(CheckInRecord::getCheckInDate)
                .collect(Collectors.toCollection(TreeSet::new));
        int[] streaks = calculateStreaks(dates, today());
        int checkedInDays = dates.size();
        BigDecimal completionRate = task.getTargetDays() == null || task.getTargetDays() <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(checkedInDays)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(task.getTargetDays()), 1, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100));

        Map<LocalDate, Integer> durationByDate = records.stream().collect(Collectors.groupingBy(
                CheckInRecord::getCheckInDate,
                Collectors.summingInt(record -> record.getDurationMinutes() == null ? 0 : record.getDurationMinutes())
        ));
        List<CheckInStatsResponse.DailyTrend> trend = new ArrayList<>();
        LocalDate today = today();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            trend.add(CheckInStatsResponse.DailyTrend.builder()
                    .date(date)
                    .checkedIn(dates.contains(date))
                    .durationMinutes(durationByDate.getOrDefault(date, 0))
                    .build());
        }

        return CheckInStatsResponse.builder()
                .taskId(task.getId())
                .taskTitle(task.getTitle())
                .targetDays(task.getTargetDays())
                .checkedInDays(checkedInDays)
                .currentStreak(streaks[0])
                .longestStreak(streaks[1])
                .completionRate(completionRate)
                .recentSevenDays(trend)
                .build();
    }

    public static int[] calculateStreaks(SortedSet<LocalDate> dates, LocalDate today) {
        if (dates.isEmpty()) return new int[]{0, 0};

        int longest = 0;
        int running = 0;
        LocalDate previous = null;
        for (LocalDate date : dates) {
            running = previous != null && date.equals(previous.plusDays(1)) ? running + 1 : 1;
            longest = Math.max(longest, running);
            previous = date;
        }

        LocalDate latest = dates.last();
        if (latest.isBefore(today.minusDays(1))) return new int[]{0, longest};
        int current = 1;
        LocalDate cursor = latest.minusDays(1);
        while (dates.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        return new int[]{current, longest};
    }

    private void validateCheckIn(Task task, LocalDate checkInDate) {
        if (task.getStartDate() == null || task.getEndDate() == null) {
            throw new BusinessException(400, "学习计划周期不完整，无法打卡");
        }
        if (checkInDate.isAfter(today())) {
            throw new BusinessException(400, "不能为未来日期打卡");
        }
        if (checkInDate.isBefore(task.getStartDate()) || checkInDate.isAfter(task.getEndDate())) {
            throw new BusinessException(400, "打卡日期不在学习计划周期内");
        }
        if (today().isBefore(task.getStartDate())) {
            throw new BusinessException(400, "学习计划尚未开始，不能打卡");
        }
        if (today().isAfter(task.getEndDate())) {
            throw new BusinessException(400, "学习计划已经过期，不能打卡");
        }
        if (task.getStatus() != PlanStatus.IN_PROGRESS) {
            throw new BusinessException(400, "只有进行中的学习计划可以打卡，当前状态为"
                    + task.getStatus().getDescription());
        }
    }

    private Task getOwnedTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "学习计划不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该学习计划");
        }
        return task;
    }

    private CheckInResponse toResponse(CheckInRecord record, String taskTitle) {
        return CheckInResponse.builder()
                .id(record.getId())
                .taskId(record.getTaskId())
                .taskTitle(taskTitle)
                .checkInDate(record.getCheckInDate())
                .content(record.getContent())
                .durationMinutes(record.getDurationMinutes())
                .mood(record.getMood())
                .automatic(Boolean.TRUE.equals(record.getAutomatic()))
                .createdAt(record.getCreatedAt())
                .build();
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }
}
