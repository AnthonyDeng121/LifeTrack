# LifeTrack Backend

Spring Boot 4 + Spring Data JPA 后端，负责认证、学习计划、正式打卡、统计和可选 AI 服务调用。

## 启动要求

- JDK 17
- MySQL 8.x
- 已创建 `lifetrack` 数据库

启动前必须设置：

```powershell
$env:DB_USERNAME = "your_mysql_username"
$env:DB_PASSWORD = "your_mysql_password"
$env:JWT_SECRET = "replace_with_a_random_secret_of_at_least_32_characters"
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run
```

可选的 `DB_URL`、`JWT_EXPIRATION` 等变量见根目录 `.env.example`。不要直接把真实凭据写入 `application*.yml`。

后端默认运行在 `http://localhost:8081`，Swagger 地址为 `http://localhost:8081/swagger-ui.html`。

## 验证

设置数据库环境变量后执行：

```powershell
.\mvnw.cmd test
```

启动后端后，可执行真实数据库验收：

```powershell
.\scripts\verify-p1-acceptance.ps1
```

AI 服务不是计划和打卡功能的必需依赖。AI 服务关闭时，手动计划、打卡、历史和统计接口仍可正常运行。

开发登录和演示手机号登录同时受环境与配置开关限制，仅显式、单独的 `dev` 环境允许。无环境配置或其他环境均关闭。使用 `AUTH_DEV_LOGIN_ENABLED=false` 或 `AUTH_DEMO_PHONE_ENABLED=false` 可在开发环境关闭对应入口。微信登录已删除，未实现真实短信认证。

错误响应的 HTTP 状态与 `code` 一致；未知服务器异常只返回通用提示，不向客户端暴露原始异常。具体配置、验证与兼容说明见根目录 `docs/异常处理与开发认证修复.md`。
