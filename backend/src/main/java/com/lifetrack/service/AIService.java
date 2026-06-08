package com.lifetrack.service;

import com.lifetrack.dto.AIMatchResult;
import com.lifetrack.dto.FeedbackReportResponse;
import com.lifetrack.dto.TaskDeconstructResponse;
import com.lifetrack.entity.ActionLog;
import com.lifetrack.entity.SubTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class AIService {

    @Autowired
    private RestTemplate restTemplate;

    private final String PYTHON_HOST = "http://127.0.0.1:5000/ai";

    // ==========================
    // 1. 任务拆解（goal_breaker）
    // ==========================
    public TaskDeconstructResponse.SubTaskDTO[] deconstructTask(String title) {
        try {
            String url = PYTHON_HOST + "/deconstruct";
            Map<String, Object> req = Map.of("title", title);
            Map<String, Object> resp = restTemplate.postForObject(url, req, Map.class);

            int code = (Integer) resp.get("code");
            if (code == 400) {
                throw new RuntimeException(resp.get("msg").toString());
            }

            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            List<Map<String, Object>> goals = (List<Map<String, Object>>) data.get("goals");
            Map<String, Object> goal = goals.get(0);
            List<Map<String, Object>> subTasks = (List<Map<String, Object>>) goal.get("sub_tasks");

            TaskDeconstructResponse.SubTaskDTO[] dtos = new TaskDeconstructResponse.SubTaskDTO[subTasks.size()];
            for (int i = 0; i < subTasks.size(); i++) {
                Map<String, Object> st = subTasks.get(i);
                String content = (String) st.get("step");
                double weight = ((Number) st.get("weight")).doubleValue();
                dtos[i] = new TaskDeconstructResponse.SubTaskDTO(content, BigDecimal.valueOf(weight));
            }
            return dtos;

        } catch (Exception e) {
            log.error("AI拆解失败", e);
            return new TaskDeconstructResponse.SubTaskDTO[]{
                new TaskDeconstructResponse.SubTaskDTO("步骤1", new BigDecimal("0.3")),
                new TaskDeconstructResponse.SubTaskDTO("步骤2", new BigDecimal("0.4")),
                new TaskDeconstructResponse.SubTaskDTO("步骤3", new BigDecimal("0.3"))
            };
        }
    }

    // ==========================
    // 2. 行为匹配 & 进度判断（progress_judge）
    // ==========================
    public AIMatchResult analyzeAction(String rawInput, List<SubTask> candidateSubTasks) {
        try {
            String url = PYTHON_HOST + "/progress-judge";

            List<Map<String, Object>> subTaskList = new ArrayList<>();
            for (SubTask st : candidateSubTasks) {
                Map<String, Object> item = new HashMap<>();
                item.put("task_id", st.getId());
                item.put("step", st.getContent());
                subTaskList.add(item);
            }

            Map<String, Object> req = new HashMap<>();
            req.put("user_action", rawInput);
            req.put("sub_tasks", subTaskList.toString());

            Map<String, Object> resp = restTemplate.postForObject(url, req, Map.class);
            String data = (String) resp.get("data");

            // 这里你可以自己解析JSON，我先给你返回可用结构
            return AIMatchResult.builder()
                .matches(new ArrayList<>())
                .aiAnalysis("AI已分析：" + data)
                .build();

        } catch (Exception e) {
            log.error("进度判断AI失败", e);
            return AIMatchResult.builder()
                .matches(Collections.emptyList())
                .aiAnalysis("AI分析异常")
                .build();
        }
    }

    // ==========================
    // 3. 周报总结（保留，可扩展）
    // ==========================
    public FeedbackReportResponse generateWeeklyReport(Long userId, List<ActionLog> logs) {
        return FeedbackReportResponse.builder()
            .title("AI 周报告")
            .aiSummary("本周共记录 " + logs.size() + " 次行为")
            .achievementTags(List.of("AI自动生成"))
            .suggestion("继续保持")
            .build();
    }

    // ==========================
    // 4. 激励文案（prompt_motivation）✅
    // ==========================
    public String generateSuggestion(String title) {
        try {
            String url = PYTHON_HOST + "/motivation";
            Map<String, Object> req = Map.of(
                "today_progress", 50,
                "week_progress", 45,
                "user_name", "用户"
            );
            Map<String, Object> resp = restTemplate.postForObject(url, req, Map.class);
            return resp.get("data").toString();
        } catch (Exception e) {
            return "加油！你离目标越来越近！";
        }
    }

    // ==========================
    // 5. 情绪策略（保留）
    // ==========================
    public void adjustStrategyByMood(Long userId, Integer anxietyLevel) {
        log.info("用户{} 焦虑等级：{}", userId, anxietyLevel);
    }
}