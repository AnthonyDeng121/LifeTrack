package com.lifetrack.service.impl;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.DashboardStatsResponse;
import com.lifetrack.dto.SubTaskContributionResponse;
import com.lifetrack.entity.ActionLog;
import com.lifetrack.entity.SubTask;
import com.lifetrack.entity.Task;
import com.lifetrack.entity.UserProfile;
import com.lifetrack.entity.CheckInRecord;
import com.lifetrack.entity.PlanStatus;
import com.lifetrack.repository.ActionLogRepository;
import com.lifetrack.repository.SubTaskRepository;
import com.lifetrack.repository.TaskRepository;
import com.lifetrack.repository.UserProfileRepository;
import com.lifetrack.repository.CheckInRecordRepository;
import com.lifetrack.service.CheckInService;
import com.lifetrack.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ActionLogRepository actionLogRepository;
    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;
    private final UserProfileRepository userProfileRepository;
    private final CheckInRecordRepository checkInRecordRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        Long userId = UserContext.getUserId();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        List<Task> allPlans = taskRepository.findByUserId(userId);
        List<Task> validPlans = allPlans.stream()
                .filter(task -> task.getStatus() != PlanStatus.CANCELLED)
                .toList();
        Set<Long> validTaskIds = validPlans.stream().map(Task::getId).collect(Collectors.toSet());
        List<CheckInRecord> allCheckIns = checkInRecordRepository
                .findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
                        userId, LocalDate.of(1970, 1, 1), today)
                .stream()
                .filter(record -> validTaskIds.contains(record.getTaskId()))
                .toList();
        int completedPlanCount = (int) validPlans.stream().filter(t -> t.getStatus() == PlanStatus.COMPLETED).count();
        BigDecimal learningPlanCompletionRate = validPlans.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(completedPlanCount * 100L).divide(BigDecimal.valueOf(validPlans.size()), 1, RoundingMode.HALF_UP);
        SortedSet<LocalDate> checkInDates = allCheckIns.stream()
                .map(CheckInRecord::getCheckInDate)
                .collect(Collectors.toCollection(TreeSet::new));
        int[] checkInStreaks = CheckInService.calculateStreaks(checkInDates, today);
        int checkedInDays = checkInDates.size();
        List<ActionLog> periodLogs = actionLogRepository.findByUserIdAndCreatedAtBetween(
                userId, today.minusDays(6).atStartOfDay(), today.atTime(LocalTime.MAX)).stream()
                .filter(log -> validTaskIds.contains(log.getTaskId())).toList();

        Map<LocalDate, List<CheckInRecord>> checkInsByDate = allCheckIns.stream()
                .collect(Collectors.groupingBy(CheckInRecord::getCheckInDate));
        List<DashboardStatsResponse.CheckInTrend> checkInTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<CheckInRecord> records = checkInsByDate.getOrDefault(date, List.of());
            checkInTrend.add(DashboardStatsResponse.CheckInTrend.builder()
                    .date(date.format(DateTimeFormatter.ofPattern("M/d")))
                    .checkInCount(records.isEmpty() ? 0 : 1)
                    .durationMinutes(records.stream().mapToInt(record ->
                            record.getDurationMinutes() == null ? 0 : record.getDurationMinutes()).sum())
                    .build());
        }

        // 1. 今日累计进度增量 (百分制)
        BigDecimal todayProgress = sumContribution(periodLogs, today);

        // 2. 今日时间分配 (从行为记录中统计)
        List<ActionLog> todayLogs = periodLogs.stream().filter(log -> log.getCreatedAt().toLocalDate().equals(today)).toList();
        
        int totalMinutes = todayLogs.stream()
                .mapToInt(log -> log.getDurationMinutes() != null ? log.getDurationMinutes() : 0)
                .sum();

        Map<Task.Category, List<ActionLog>> logsByCategory = todayLogs.stream()
                .filter(log -> log.getCategory() != null)
                .collect(Collectors.groupingBy(ActionLog::getCategory));

        List<DashboardStatsResponse.TimeDistribution> distribution = new ArrayList<>();
        logsByCategory.forEach((cat, logs) -> {
            int catMinutes = logs.stream()
                    .mapToInt(log -> log.getDurationMinutes() != null ? log.getDurationMinutes() : 0)
                    .sum();
            
            BigDecimal percentage = totalMinutes > 0 
                    ? new BigDecimal(catMinutes).multiply(new BigDecimal("100")).divide(new BigDecimal(totalMinutes), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            List<DashboardStatsResponse.BehaviorDetail> behaviors = logs.stream()
                    .map(log -> DashboardStatsResponse.BehaviorDetail.builder()
                            .actionName(log.getRawInput())
                            .minutes(log.getDurationMinutes() != null ? log.getDurationMinutes() : 0)
                            .build())
                    .collect(Collectors.toList());

            distribution.add(DashboardStatsResponse.TimeDistribution.builder()
                    .name(cat.name())
                    .minutes(catMinutes)
                    .percentage(percentage)
                    .behaviors(behaviors)
                    .build());
        });

        // 3. 周趋势 (过去7天)
        List<DashboardStatsResponse.WeeklyTrend> weeklyTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            BigDecimal dayInc = sumContribution(periodLogs, date);
            weeklyTrend.add(DashboardStatsResponse.WeeklyTrend.builder()
                    .date(date.format(DateTimeFormatter.ofPattern("M/d")))
                    .progress(dayInc)
                    .build());
        }

        // 4. 获取当前焦虑等级
        Integer anxietyLevel = userProfileRepository.findByUserId(userId)
                .map(UserProfile::getCurrentAnxietyLevel)
                .orElse(1);

        return DashboardStatsResponse.builder()
                .todayTotalProgress(todayProgress)
                .timeDistribution(distribution)
                .weeklyTrend(weeklyTrend)
                .learningPlanCompletionRate(learningPlanCompletionRate)
                .completedPlanCount(completedPlanCount)
                .totalPlanCount(validPlans.size())
                .cancelledPlanCount((int) allPlans.stream().filter(t -> t.getStatus() == PlanStatus.CANCELLED).count())
                .checkedInDays(checkedInDays)
                .currentCheckInStreak(checkInStreaks[0])
                .longestCheckInStreak(checkInStreaks[1])
                .checkInTrend(checkInTrend)
                .currentAnxietyLevel(anxietyLevel)
                .dailyQuote(generateQuote(todayProgress))
                .build();
    }

    @Override
    public SubTaskContributionResponse getTaskContribution(Long taskId) {
        Long userId = UserContext.getUserId();
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new com.lifetrack.exception.BusinessException(404, "任务不存在"));
        
        // 增加权限校验
        if (!task.getUserId().equals(userId)) {
            throw new com.lifetrack.exception.BusinessException(403, "无权查看该任务数据");
        }
        
        List<SubTask> subTasks = subTaskRepository.findByTaskIdOrderByOrderNumAsc(taskId);
        
        List<SubTaskContributionResponse.SubTaskContribution> contributionList = subTasks.stream()
                .map(st -> {
                    // 对主任务的贡献 = 当前进度 * 权重 * 100
                    BigDecimal contribution = st.getCurrentProgress()
                            .multiply(st.getWeight())
                            .multiply(new BigDecimal("100"))
                            .setScale(1, RoundingMode.HALF_UP);
                    
                    return SubTaskContributionResponse.SubTaskContribution.builder()
                            .id(st.getId())
                            .content(st.getContent())
                            .weight(st.getWeight())
                            .currentProgress(st.getCurrentProgress().multiply(new BigDecimal("100")))
                            .totalContributionToMainTask(contribution)
                            .build();
                })
                .collect(Collectors.toList());

        return SubTaskContributionResponse.builder()
                .taskTitle(task.getTitle())
                .totalProgress(task.getTotalProgress().multiply(new BigDecimal("100")))
                .subTasks(contributionList)
                .build();
    }

    private BigDecimal sumContribution(List<ActionLog> logs, LocalDate date) {
        return logs
                .stream()
                .filter(log -> log.getCreatedAt().toLocalDate().equals(date))
                .map(log -> log.getContribution() != null ? log.getContribution() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(new BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private String generateQuote(BigDecimal progress) {
        if (progress.compareTo(new BigDecimal("80")) >= 0) return "今天的你简直是效率之神！";
        if (progress.compareTo(new BigDecimal("50")) >= 0) return "任务过半，晚上的休息时间已经赚到了。";
        if (progress.compareTo(new BigDecimal("20")) >= 0) return "渐入佳境，继续保持节奏。";
        return "万事开头难，哪怕只走出一小步也是胜利。";
    }
}
