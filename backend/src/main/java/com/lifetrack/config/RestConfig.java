package com.lifetrack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RestConfig {
    @Bean
    public RestTemplate restTemplate(@Value("${ai.read-timeout-seconds:40}") int readTimeoutSeconds,
            @Value("${ai.deconstruct-read-timeout-seconds:90}") int deconstructReadTimeoutSeconds) {
        int deconstructMillis = Math.toIntExact(Duration.ofSeconds(deconstructReadTimeoutSeconds).toMillis());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                // 拆解包含一次质量修复重试；其他分析请求继续使用较短的独立预算。
                if (connection.getURL().getPath().endsWith("/deconstruct")) connection.setReadTimeout(deconstructMillis);
            }
        };
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        // 确保 JSON 使用 UTF-8
        restTemplate.getMessageConverters().forEach(converter -> {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            } else if (converter instanceof MappingJackson2HttpMessageConverter) {
                ((MappingJackson2HttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
        });
        return restTemplate;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
