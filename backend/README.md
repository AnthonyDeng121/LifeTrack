# LifeTrack Backend

LifeTrack 后端服务基于 Spring Boot 3 开发，负责核心业务逻辑处理、数据持久化及 AI 服务的调用。

## 🛠 技术栈
- **核心框架**: Spring Boot 3.2.x
- **数据库**: MySQL 8.0
- **持久层**: MyBatis Plus
- **安全认证**: JWT (JSON Web Token)
- **API 文档**: SpringDoc OpenAPI (Swagger)

## 🚀 启动指南

### 1. 环境准备
- **JDK**: 17 或更高版本
- **Maven**: 3.6+ (项目自带 `mvnw`)
- **MySQL**: 8.0

### 2. 数据库配置
1. 创建数据库 `lifetrack`。
2. 运行 `sql/init_db.sql` 脚本。
3. 修改 `src/main/resources/application-dev.yml` 中的 `datasource` 配置（如果你的本地数据库账号密码不同）。

### 3. 环境变量 (可选)
后端调用 AI 服务需要配置 AI 服务的地址（默认 `http://localhost:5000`）。
如果需要直接调用 OpenAI (作为备份)，请设置环境变量：
- `OPENAI_API_KEY`: 你的 OpenAI API Key

### 4. 运行
```bash
# Windows
./mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

## 📄 接口文档
启动后访问：[http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)

## 📁 目录结构
- `controller`: RESTful 接口层
- `service`: 业务逻辑层
- `repository`: 数据库访问层
- `entity`: 数据库实体类
- `dto`: 数据传输对象
- `common`: 通用配置、拦截器、工具类
- `config`: Spring 配置类
