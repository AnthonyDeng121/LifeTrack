package com.lifetrack.service;

import com.lifetrack.dto.AIMatchResult;
import com.lifetrack.dto.FeedbackReportResponse;
import com.lifetrack.dto.TaskDeconstructResponse;
import com.lifetrack.entity.ActionLog;
import com.lifetrack.entity.SubTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * AI 服务核心接口
 * 由 AI 负责人 (李想/魏欣岳) 负责具体实现 DeepSeek API 的调用与 Prompt 调优
 */
@Slf4j
@Service
public class AIService {

    /**
     * 1. 任务拆解接口
     * 用户输入目标，返回拆解后的子任务 JSON 列表
     */
    public TaskDeconstructResponse.SubTaskDTO[] deconstructTask(String title) {
        // TODO: 李想/魏欣岳 对接 DeepSeek API
        // 建议 Prompt: 将「%s」拆解为 JSON 数组: [{"content": "...", "weight": 0.2}]
        
        log.warn("任务拆解 AI 接口待实现，当前返回 Mock 数据");
        return new TaskDeconstructResponse.SubTaskDTO[]{
            new TaskDeconstructResponse.SubTaskDTO("环境搭建", new BigDecimal("0.2")),
            new TaskDeconstructResponse.SubTaskDTO("核心开发", new BigDecimal("0.6")),
            new TaskDeconstructResponse.SubTaskDTO("测试发布", new BigDecimal("0.2"))
        };
    }

    /**
     * 2. 行为匹配接口 (1对N)
     * 分析用户输入，匹配所有相关的子任务并给出进度增量
     */
    public AIMatchResult analyzeAction(String rawInput, List<SubTask> candidateSubTasks) {
        // TODO: 李想/魏欣岳 对接 DeepSeek API
        // 建议 Prompt: 根据行为「%s」和子任务列表，返回 JSON: {"matches": [{"subTaskId": 1, "increment": 0.05}], "aiAnalysis": "..."}
        
        log.warn("行为匹配 AI 接口待实现，当前返回 Mock 数据");
        if (candidateSubTasks.isEmpty()) return AIMatchResult.builder().matches(Collections.emptyList()).build();
        
        return AIMatchResult.builder()
                .matches(Collections.singletonList(
                        new AIMatchResult.MatchDetail(candidateSubTasks.get(0).getId(), new BigDecimal("0.05"))
                ))
                .aiAnalysis("（Mock 消息）AI 已成功识别该行为。")
                .build();
    }

    /**
     * 3. 情绪报告生成
     * 基于行为日志生成深度总结
     */
    public FeedbackReportResponse generateWeeklyReport(Long userId, List<ActionLog> logs) {
        // TODO: 李想/魏欣岳 对接 DeepSeek API
        
        log.warn("周报生成 AI 接口待实现，当前返回 Mock 数据");
        return FeedbackReportResponse.builder()
                .title("本周成就总结：稳步前行")
                .aiSummary("你在本周完成了 " + logs.size() + " 项记录。")
                .achievementTags(Arrays.asList("持续记录"))
                .suggestion("继续保持节奏。")
                .build();
    }

    /**
     * 4. 激励建议生成 (用于首页展示)
     */
    public String generateSuggestion(String title) {
        return "这是一个非常棒的目标！建议分解为小步骤，每天稳步推进。";
    }

    /**
     * 5. 情绪策略调整
     */
    public void adjustStrategyByMood(Long userId, Integer anxietyLevel) {
        log.info("AI 策略已针对用户 {} 调整，当前焦虑等级: {}", userId, anxietyLevel);
    }
}
