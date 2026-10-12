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
    void shouldReturnExplicitServiceUnavailableInsteadOfFakeDeconstruction() {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> aiService.deconstructTask("学习 Spring Boot"));

        assertEquals(503, error.getCode());
        assertEquals("AI 拆解服务暂不可用，请切换手动创建计划", error.getMessage());
    }
}
