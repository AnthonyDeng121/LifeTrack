# LifeTrack Backend

Spring Boot 4 + Spring Data JPA 后端，负责认证、学习计划、正式打卡、统计和可选 AI 服务调用。

## 启动要求

- JDK 17
- MySQL 8.0.16+，最近实际验证为 8.0.45
- 已执行 `sql/init_db.sql` 创建数据库、表和约束；只有空库名而没有结构仍不能启动

首次接管在完成总脚本后临时设置 `FLYWAY_ADOPT_BASELINE=true`，启动成功移除，登记版本 1 基线。日常启动不再设置该开关，不重放总脚本。以后变更新增 V2+；Hibernate 只 validate。完整新机器流程见 [提交清单与使用说明](../docs/delivery/提交清单与使用说明.md)。

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

推荐回到根目录，由脚本创建并清理隔离库后运行完整验收：

```powershell
cd ..
.\verify-course.ps1
```

单独执行后端 `mvnw.cmd test` 时必须设置 `TEST_DB_URL`、`TEST_DB_USERNAME` 和 `TEST_DB_PASSWORD`，数据库名只能以 `lifetrack_test_` 开头，且已执行总脚本。测试不会回退到日常 DB_*。

在 backend 目录、后端已经启动时，可执行现场 HTTP 验收：

```powershell
.\scripts\verify-p1-acceptance.ps1
```

该脚本默认使用共享开发账号，只清理本次临时计划；直接 SQL 和并发证据由隔离集成测试提供。开发设计、数据结构、事务和完整交付入口见 [课程材料索引](../docs/交付材料索引.md)。

AI 服务不是计划和打卡功能的必需依赖。AI 服务关闭时，手动计划、打卡、历史和统计接口仍可正常运行。

开发登录和演示手机号登录同时受环境与配置开关限制，仅显式、单独的 `dev` 环境允许。无环境配置或其他环境均关闭。使用 `AUTH_DEV_LOGIN_ENABLED=false` 或 `AUTH_DEMO_PHONE_ENABLED=false` 可在开发环境关闭对应入口。微信登录已删除，未实现真实短信认证。

错误响应的 HTTP 状态与 `code` 一致；未知服务器异常只返回通用提示，不向客户端暴露原始异常。具体配置、验证与兼容说明见根目录 `docs/异常处理与开发认证修复.md`。
