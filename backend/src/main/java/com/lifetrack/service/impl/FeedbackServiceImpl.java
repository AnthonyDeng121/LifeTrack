package com.lifetrack.service.impl;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.FeedbackReportResponse;
import com.lifetrack.dto.UserMoodUpdateRequest;
import com.lifetrack.entity.ActionLog;
import com.lifetrack.entity.UserProfile;
import com.lifetrack.repository.ActionLogRepository;
import com.lifetrack.repository.UserProfileRepository;
import com.lifetrack.service.AIService;
import com.lifetrack.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final ActionLogRepository actionLogRepository;
    private final UserProfileRepository userProfileRepository;
    private final AIService aiService;
    private final com.lifetrack.repository.TaskRepository taskRepository;
    private final com.lifetrack.repository.CheckInRecordRepository checkInRecordRepository;

    @Override
    public FeedbackReportResponse getFeedbackReport() {
        Long userId = UserContext.getUserId();
        
        // 获取过去7天的行为记录作为分析样本
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        LocalDateTime startTime = now.minusDays(7);
        List<com.lifetrack.entity.Task> plans = taskRepository.findByUserId(userId).stream()
                .filter(t -> t.getStatus() != com.lifetrack.entity.PlanStatus.CANCELLED).toList();
        java.util.Set<Long> planIds = plans.stream().map(com.lifetrack.entity.Task::getId).collect(java.util.stream.Collectors.toSet());
        List<ActionLog> logs = actionLogRepository.findByUserIdAndCreatedAtBetween(userId, startTime, now).stream()
                .filter(log -> planIds.contains(log.getTaskId())).toList();
        java.util.Set<java.time.LocalDate> attendedDates = new java.util.HashSet<>();

        java.util.Map<Long, java.util.Set<java.time.LocalDate>> planDates = planIds.isEmpty() ? java.util.Map.of()
                : checkInRecordRepository.findByUserIdAndTaskIdIn(userId, planIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(com.lifetrack.entity.CheckInRecord::getTaskId,
                        java.util.stream.Collectors.mapping(com.lifetrack.entity.CheckInRecord::getCheckInDate, java.util.stream.Collectors.toSet())));
        // 调用 AI 生成报告
        List<java.util.Map<String, Object>> outcomes = plans.stream()
                .map(t -> {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("title", t.getTitle());
                    row.put("status", t.getStatus());
                    row.put("learning_progress", t.getTotalProgress());
                    row.put("target_days", t.getTargetDays());
                    java.util.Set<java.time.LocalDate> dates = planDates.getOrDefault(t.getId(), java.util.Set.of());
                    attendedDates.addAll(dates);
                    row.put("actual_check_in_days", dates.size());
                    return row;
                }).toList();
        FeedbackReportResponse report = aiService.generateWeeklyReport(userId, logs, outcomes);
        long completed = outcomes.stream().filter(row -> row.get("status") == com.lifetrack.entity.PlanStatus.COMPLETED).count();
        report.setPlanOutcomeSummary("当前学习计划：已完成 " + completed + " / " + outcomes.size()
                + " 项。未取消计划累计打卡 " + attendedDates.size()
                + " 天（同日去重）；已取消计划的历史保留但不计入看板分析。学习成果按实际完成计划评价。");
        return report;
    }

    @Override
    @Transactional
    public void updateMood(UserMoodUpdateRequest request) {
        Long userId = UserContext.getUserId();
        
        // 1. 更新数据库中的焦虑等级
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(UserProfile.builder().userId(userId).build());
        
        profile.setCurrentAnxietyLevel(request.getAnxietyLevel());
        userProfileRepository.save(profile);

        // 2. 通知 AI 引擎调整策略
        aiService.adjustStrategyByMood(userId, request.getAnxietyLevel());
    }
}
