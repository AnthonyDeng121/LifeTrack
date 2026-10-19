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
        List<ActionLog> logs = actionLogRepository.findByUserIdAndCreatedAtBetween(
                userId, startTime, now);

        // 调用 AI 生成报告
        List<java.util.Map<String, Object>> outcomes = taskRepository.findByUserId(userId).stream()
                .filter(t -> t.getStatus() != com.lifetrack.entity.PlanStatus.CANCELLED)
                .map(t -> {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("title", t.getTitle());
                    row.put("status", t.getStatus());
                    row.put("learning_progress", t.getTotalProgress());
                    row.put("target_days", t.getTargetDays());
                    row.put("actual_check_in_days", checkInRecordRepository.findByUserIdAndTaskIdOrderByCheckInDateAsc(userId, t.getId()).size());
                    return row;
                }).toList();
        FeedbackReportResponse report = aiService.generateWeeklyReport(userId, logs, outcomes);
        long completed = outcomes.stream().filter(row -> row.get("status") == com.lifetrack.entity.PlanStatus.COMPLETED).count();
        int attended = outcomes.stream().mapToInt(row -> (Integer) row.get("actual_check_in_days")).sum();
        int target = outcomes.stream().mapToInt(row -> row.get("target_days") instanceof Integer days ? days : 0).sum();
        report.setPlanOutcomeSummary("当前学习计划：已完成 " + completed + " / " + outcomes.size()
                + " 项。坚持记录：累计打卡 " + attended + " 次，原定目标合计 " + target
                + " 天。学习成果与坚持目标分别评价，提前完成同样计入成果。");
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
