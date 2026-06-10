package com.lifetrack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private ObjectMapper objectMapper;

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
            log.error("AI拆解失败，使用兜底逻辑", e);
            return new TaskDeconstructResponse.SubTaskDTO[]{
                new TaskDeconstructResponse.SubTaskDTO("基础准备与规划", new BigDecimal("0.2")),
                new TaskDeconstructResponse.SubTaskDTO("核心环节执行", new BigDecimal("0.5")),
                new TaskDeconstructResponse.SubTaskDTO("成果验收与复盘", new BigDecimal("0.3"))
            };
        }
    }

    // ==========================
    // 2. 行为匹配 & 进度判断（progress_judge）
    // ==========================
    public AIMatchResult analyzeAction(String rawInput, String durationInput, List<SubTask> candidateSubTasks) {
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
            req.put("duration_input", durationInput);
            req.put("sub_tasks", objectMapper.writeValueAsString(subTaskList));

            Map<String, Object> resp = restTemplate.postForObject(url, req, Map.class);
            if (resp == null || (Integer) resp.get("code") != 200) {
                throw new RuntimeException("AI Server Error: " + (resp != null ? resp.get("msg") : "null"));
            }

            String jsonData = (String) resp.get("data");

            // 解析 Python 返回的 JSON 字符串
            Map<String, Object> dataMap = objectMapper.readValue(jsonData, Map.class);
            List<Map<String, Object>> matchesRaw = (List<Map<String, Object>>) dataMap.get("matches");
            String suggestedReply = (String) dataMap.get("suggested_reply");
            Integer durationMinutes = (Integer) dataMap.get("duration_minutes");

            List<AIMatchResult.MatchDetail> matches = new ArrayList<>();
            if (matchesRaw != null) {
                for (Map<String, Object> m : matchesRaw) {
                    Object tid = m.get("task_id");
                    Object cont = m.get("contribution");
                    if (tid != null && cont != null) {
                        matches.add(AIMatchResult.MatchDetail.builder()
                                .subTaskId(((Number) tid).longValue())
                                .increment(BigDecimal.valueOf(((Number) cont).doubleValue()))
                                .build());
                    }
                }
            }

            return AIMatchResult.builder()
                .matches(matches)
                .aiAnalysis(suggestedReply)
                .durationMinutes(durationMinutes)
                .build();

        } catch (Exception e) {
            log.error("进度判断AI失败", e);
            return AIMatchResult.builder()
                .matches(Collections.emptyList())
                .aiAnalysis("AI 分析记录失败，请稍后重试")
                .build();
        }
    }

    // ==========================
    // 3. 周报总结（AI 生成）
    // ==========================
    public FeedbackReportResponse generateWeeklyReport(Long userId, List<ActionLog> logs) {
        try {
            String url = PYTHON_HOST + "/report";
            
            List<String> logTexts = logs.stream()
                    .map(log -> log.getRawInput() + " (贡献度:" + log.getContribution() + ")")
                    .toList();
            
            Map<String, Object> req = Map.of("logs", logTexts);
            Map<String, Object> resp = restTemplate.postForObject(url, req, Map.class);

            if (resp != null && (Integer) resp.get("code") == 200) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                return FeedbackReportResponse.builder()
                        .title((String) data.get("title"))
                        .aiSummary((String) data.get("ai_summary"))
                        .achievementTags((List<String>) data.get("achievement_tags"))
                        .suggestion((String) data.get("suggestion"))
                        .build();
            }
        } catch (Exception e) {
            log.error("AI 生成周报失败", e);
        }
        
        // 兜底逻辑
        return FeedbackReportResponse.builder()
            .title("AI 周报告 (生成失败)")
            .aiSummary("本周共记录 " + logs.size() + " 次行为，暂无法生成深度分析。")
            .achievementTags(List.of("系统生成"))
            .suggestion("请检查 AI 服务状态")
            .build();
    }

    // ==========================
    // 4. 激励文案（prompt_motivation）✅
    // ==========================
    public String generateSuggestion(int todayProgress, int weekProgress, String username) {
        try {
            String url = PYTHON_HOST + "/motivation";
            Map<String, Object> req = Map.of(
                "today_progress", todayProgress,
                "week_progress", weekProgress,
                "user_name", username
            );
            Map<String, Object> resp = restTemplate.postForObject(url, req, Map.class);
            return resp.get("data").toString();
        } catch (Exception e) {
            return "加油！每一小步都在让你变得更好！";
        }
    }

    // ==========================
    // 5. 情绪策略（通知 AI 引擎）
    // ==========================
    public void adjustStrategyByMood(Long userId, Integer anxietyLevel) {
        try {
            String url = PYTHON_HOST + "/adjust-strategy";
            Map<String, Object> req = Map.of(
                "user_id", userId,
                "anxiety_level", anxietyLevel
            );
            restTemplate.postForObject(url, req, Map.class);
        } catch (Exception e) {
            log.error("通知 AI 情绪策略失败", e);
        }
    }

    public String generateMoodQuote(Integer anxietyLevel, String username) {
        try {
            String url = PYTHON_HOST + "/mood-quote";
            Map<String, Object> req = Map.of(
                "anxiety_level", anxietyLevel,
                "username", username
            );
            Map<String, Object> resp = restTemplate.postForObject(url, req, Map.class);
            return resp.get("data").toString();
        } catch (Exception e) {
            return "愿你今天拥有好心情。";
        }
    }
}