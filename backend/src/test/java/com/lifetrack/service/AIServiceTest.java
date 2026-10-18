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
    void shouldReturnExplicitServiceUnavailableInsteadOfFakeDeconstruction() {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> aiService.deconstructTask("学习 Spring Boot"));

        assertEquals(503, error.getCode());
        assertEquals("AI 拆解服务暂不可用，请切换手动创建计划", error.getMessage());
    }
}
