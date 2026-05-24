package com.lifetrack.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;

/**
 * OpenAPI/Swagger 配置类
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lifeTrackOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LifeTrack API 接口文档")
                        .description("LifeTrack 项目后端接口说明文档，包含任务管理、活动追踪等功能。")
                        .version("v0.0.1")
                        .contact(new Contact()
                                .name("LifeTrack Team")
                                .email("support@lifetrack.com")))
                .tags(Arrays.asList(
                        new Tag().name("认证管理").description("用户登录与注册相关接口"),
                        new Tag().name("任务管理").description("提供任务拆解、AI 辅助等功能"),
                        new Tag().name("行为记录").description("用户行为同步与 AI 贡献度分析")
                ));
    }
}
