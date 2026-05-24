package com.lifetrack.service.impl;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.ActionSyncRequest;
import com.lifetrack.dto.ActionSyncResponse;
import com.lifetrack.entity.ActionLog;
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
        
        BigDecimal mockIncrement = new BigDecimal("5.0");
        String mockMatchedTask = "学会 Spring Boot 开发";
        String mockAiAnalysis = "干得漂亮！你已经掌握了 Web 开发的核心。";

        // 2. 持久化行为日志
        ActionLog actionLog = ActionLog.builder()
                .userId(userId)
                .rawInput(request.getRawInput())
                .contribution(mockIncrement)
                .aiAnalysis(mockAiAnalysis)
                .build();
        actionLogRepository.save(actionLog);

        // 3. 更新任务进度 (此处逻辑暂略，后续对接 TaskService)

        return ActionSyncResponse.builder()
                .matchedTask(mockMatchedTask)
                .increment(mockIncrement)
                .newTotalProgress(new BigDecimal("72.5")) // 假设原进度 67.5
                .aiAnalysis(mockAiAnalysis)
                .build();
    }
}
