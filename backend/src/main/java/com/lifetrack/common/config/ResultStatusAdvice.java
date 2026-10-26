package com.lifetrack.common.config;

import com.lifetrack.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 保持统一响应体，同时让HTTP状态真实表达业务失败。 */
@RestControllerAdvice
public class ResultStatusAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
        return Result.class.isAssignableFrom(type.getParameterType());
    }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType media,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result && result.getCode() != null && result.getCode() >= 400 && result.getCode() <= 599) {
            response.setStatusCode(HttpStatusCode.valueOf(result.getCode()));
        }
        return body;
    }
}
