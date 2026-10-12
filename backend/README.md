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
