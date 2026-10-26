package com.lifetrack.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.net.URI;
import java.util.Map;

/** 仅在 test classpath 注册；任何 Spring 集成测试都不得回退到日常数据库。 */
public class IsolatedDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static void requireIsolatedUrl(String url) {
        try {
            if (url == null || !url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
            String database = URI.create(url.substring(5)).getPath();
            if (database == null || !database.matches("/lifetrack_test_[a-zA-Z0-9_]+")) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("集成测试只允许 TEST_DB_URL 指向 lifetrack_test_* 隔离库。请在根目录运行 .\\verify-course.ps1，不允许连接日常 lifetrack 数据库。");
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var env = context.getEnvironment();
        String url = env.getProperty("TEST_DB_URL");
        requireIsolatedUrl(url);
        String user = env.getProperty("TEST_DB_USERNAME"), password = env.getProperty("TEST_DB_PASSWORD");
        if (user == null || user.isBlank() || password == null || password.isBlank())
            throw new IllegalStateException("请配置 TEST_DB_USERNAME/TEST_DB_PASSWORD，或使用 verify-course.ps1。");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-test-database", Map.of(
                "spring.datasource.url", url,
                "spring.datasource.username", user,
                "spring.datasource.password", password,
                "spring.jpa.hibernate.ddl-auto", "validate",
                "FLYWAY_ADOPT_BASELINE", "true")));
    }
}
