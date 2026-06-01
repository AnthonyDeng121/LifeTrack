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

    @Override
    public FeedbackReportResponse getFeedbackReport() {
        Long userId = UserContext.getUserId();
        
        // 获取过去7天的行为记录作为分析样本
        LocalDateTime startTime = LocalDateTime.now().minusDays(7);
        List<ActionLog> logs = actionLogRepository.findByUserIdAndCreatedAtBetween(
                userId, startTime, LocalDateTime.now());

        // 调用 AI 生成报告
        return aiService.generateWeeklyReport(userId, logs);
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
