package com.lifetrack.service.impl;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.DashboardStatsResponse;
import com.lifetrack.dto.SubTaskContributionResponse;
import com.lifetrack.entity.ActionLog;
import com.lifetrack.entity.SubTask;
import com.lifetrack.entity.Task;
import com.lifetrack.repository.ActionLogRepository;
import com.lifetrack.repository.SubTaskRepository;
import com.lifetrack.repository.TaskRepository;
import com.lifetrack.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ActionLogRepository actionLogRepository;
    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;

    @Override
    public DashboardStatsResponse getStats() {
        Long userId = UserContext.getUserId();
        LocalDate today = LocalDate.now();

        // 1. 今日累计进度增量 (百分制)
        BigDecimal todayProgress = sumContribution(userId, today, today);

        // 2. 今日时间分配 (从行为记录中统计)
        List<ActionLog> todayLogs = actionLogRepository.findByUserIdAndCreatedAtBetween(
                userId, today.atStartOfDay(), today.atTime(LocalTime.MAX));
        
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
            BigDecimal dayInc = sumContribution(userId, date, date);
            weeklyTrend.add(DashboardStatsResponse.WeeklyTrend.builder()
                    .date(date.format(DateTimeFormatter.ofPattern("M/d")))
                    .progress(dayInc)
                    .build());
        }

        return DashboardStatsResponse.builder()
                .todayTotalProgress(todayProgress)
                .timeDistribution(distribution)
                .weeklyTrend(weeklyTrend)
                .dailyQuote(generateQuote(todayProgress))
                .build();
    }

    @Override
    public SubTaskContributionResponse getTaskContribution(Long taskId) {
        Long userId = UserContext.getUserId();
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));
        
        // 增加权限校验
        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看该任务数据");
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

    private BigDecimal sumContribution(Long userId, LocalDate start, LocalDate end) {
        return actionLogRepository.findByUserIdAndCreatedAtBetween(userId, 
                start.atStartOfDay(), end.atTime(LocalTime.MAX))
                .stream()
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
