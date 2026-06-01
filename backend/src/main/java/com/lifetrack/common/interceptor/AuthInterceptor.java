package com.lifetrack.common.interceptor;

import com.lifetrack.common.UserContext;
import com.lifetrack.common.annotation.LoginRequired;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 如果不是映射到方法，直接通过
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();

        // 2. 检查是否有 @LoginRequired 注解
        LoginRequired classAnnotation = handlerMethod.getBeanType().getAnnotation(LoginRequired.class);
        LoginRequired methodAnnotation = method.getAnnotation(LoginRequired.class);

        // 如果类或方法上有 @LoginRequired 且 required 为 true
        if ((classAnnotation != null && classAnnotation.required()) || (methodAnnotation != null && methodAnnotation.required())) {
            // 3. 获取 Token
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header");
                throw new BusinessException(401, "未登录或 Token 已过期");
            }

            String token = authHeader.substring(7);
            try {
                // 4. 校验 Token 并解析
                Claims claims = jwtUtil.parseToken(token);
                Long userId = claims.get("userId", Long.class);
                String username = claims.getSubject();

                // 5. 存入上下文
                UserContext.setUserId(userId);
                UserContext.setUsername(username);
                
                return true;
            } catch (Exception e) {
                log.error("Token validation failed: {}", e.getMessage());
                throw new BusinessException(401, "无效的 Token");
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理上下文，防止内存泄漏
        UserContext.clear();
    }
}
