package com.lifetrack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetrack.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIServiceTest {
    @Mock private RestTemplate restTemplate;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private AIService aiService;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reportMergesOneInputAcrossStepsWithoutSummingStepPercentages() {
        var at = java.time.LocalDateTime.of(2026, 10, 19, 10, 0);
        var first = com.lifetrack.entity.ActionLog.builder().rawInput("查询练习与事务验证").createdAt(at)
                .durationMinutes(10).contribution(new java.math.BigDecimal("0.1")).build();
        var second = com.lifetrack.entity.ActionLog.builder().rawInput("查询练习与事务验证").createdAt(at)
                .durationMinutes(20).contribution(new java.math.BigDecimal("0.2")).build();
        when(restTemplate.postForObject(anyString(), any(), any(Class.class))).thenReturn(Map.of("code", 200,
                "data", Map.of("title", "周报", "ai_summary", "总结", "achievement_tags", List.of(), "suggestion", "建议")));
        aiService.generateWeeklyReport(7L, List.of(first, second), List.of(Map.of("status", "COMPLETED")));
        var payload = org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(restTemplate).postForObject(anyString(), payload.capture(), org.mockito.ArgumentMatchers.eq(Map.class));
        var texts = (List<String>) payload.getValue().get("logs");
        assertEquals(1, texts.size());
        assertEquals("查询练习与事务验证（学习时长：30分钟）", texts.get(0));
        assertEquals(List.of(Map.of("status", "COMPLETED")), payload.getValue().get("plan_outcomes"));
    }

    @Test
    void shouldSignalUnavailableWithoutInventingProgress() throws Exception {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        var result = aiService.analyzeAction("学习编曲", "30分钟", List.of());
        assertEquals(true, result.isUnavailable());
        assertEquals(0, result.getMatches().size());
    }

    @Test
    void shouldReadValidGlmDeconstruction() {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenReturn(Map.of("code", 200, "data", Map.of("goals", List.of(
                        Map.of("sub_tasks", List.of(Map.of("step", "Practice", "weight", 1)))))));
        var steps = aiService.deconstructTask("Learn Java");
        assertEquals(1, steps.length);
        assertEquals("Practice", steps[0].getContent());
    }

    @Test
    void shouldRejectUpstreamFailureAndEmptySteps() {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenReturn(Map.of("code", 500, "msg", "provider failed"))
                .thenReturn(Map.of("code", 200, "data", Map.of("goals", List.of(
                        Map.of("sub_tasks", List.of())))));
        assertThrows(BusinessException.class, () -> aiService.deconstructTask("Learn Java"));
        assertThrows(BusinessException.class, () -> aiService.deconstructTask("Learn Java"));
    }

    @Test
    void shouldDistinguishRejectedDraftFromUnavailableProvider() {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenReturn(Map.of("code", 502, "msg", "wrong step count"));
        var error = assertThrows(BusinessException.class, () -> aiService.deconstructTask("网络课程"));
        assertEquals(502, error.getCode());
    }

    @Test
    void shouldReturnExplicitServiceUnavailableInsteadOfFakeDeconstruction() {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> aiService.deconstructTask("学习 Spring Boot"));

        assertEquals(503, error.getCode());
        assertEquals("AI 拆解服务暂不可用，请切换手动创建计划", error.getMessage());
    }
}
