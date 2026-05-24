package com.lifetrack.service.impl;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.ActionSyncRequest;
import com.lifetrack.dto.ActionSyncResponse;
import com.lifetrack.entity.ActionLog;
import com.lifetrack.entity.Task;
import com.lifetrack.repository.ActionLogRepository;
import com.lifetrack.service.ActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionServiceImpl implements ActionService {

    private final ActionLogRepository actionLogRepository;

    @Override
    @Transactional
    public ActionSyncResponse syncAction(ActionSyncRequest request) {
        Long userId = UserContext.getUserId();
        log.info("User {} is syncing action: {}", userId, request.getRawInput());

        // 1. 调用 AI Agent 进行分析 (此处为 Mock 逻辑)
        // 实际逻辑应为：获取该用户所有进行中的任务 -> 发送给 AI -> AI 返回匹配的任务 ID 和贡献度
        
        BigDecimal mockIncrement = new BigDecimal("0.05"); // 5% 增量
        String mockMatchedTask = "学会 Spring Boot 开发";
        String mockAiAnalysis = "干得漂亮！你已经掌握了 Web 开发的核心。";
        Task.Category mockCategory = Task.Category.学习;
        Integer mockDuration = 45; // 假设投入了45分钟

        // 2. 持久化行为日志
        ActionLog actionLog = ActionLog.builder()
                .userId(userId)
                .rawInput(request.getRawInput())
                .contribution(mockIncrement)
                .category(mockCategory)
                .durationMinutes(mockDuration)
                .aiAnalysis(mockAiAnalysis)
                .build();
        actionLogRepository.save(actionLog);

        // 3. 更新任务进度 (此处逻辑暂略，后续对接 TaskService)
        // 实际上这里应该更新 Task 和 SubTask 的进度字段，为了演示看板效果，假设进度已更新
        
        return ActionSyncResponse.builder()
                .matchedTask(mockMatchedTask)
                .increment(mockIncrement.multiply(new BigDecimal("100")))
                .newTotalProgress(new BigDecimal("72.5"))
                .aiAnalysis(mockAiAnalysis)
                .build();
    }
}
