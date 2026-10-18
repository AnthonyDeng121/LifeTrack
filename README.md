# LifeTrack AI 学习计划与智能打卡系统

LifeTrack 是“企业级 AI 软件设计与开发”课程综合实践项目，对应“学习计划与打卡系统”选题。系统以真实的计划、每日打卡和统计闭环为核心，并提供可选的 AI 计划拆解、自然语言学习记录和反馈报告。

AI 服务不是基础业务的运行前提。AI 不可用时，用户仍可手动创建、查看、编辑和删除计划，完成每日打卡并查看历史和统计。

## 核心功能

- 学习计划新增、详情、编辑、删除，以及按状态和日期筛选。
- 设置开始日期、结束日期、目标打卡天数和计划状态。
- 未开始、进行中、已完成、已取消和已过期状态及合法流转。
- 每日正式打卡，记录日期、学习内容、时长和可选心情。
- 按计划和日期查询打卡历史。
- 数据库与服务层共同阻止同一用户、同一计划、同一天重复打卡。
- 处理非法周期、未来或周期外打卡、非法状态、空输入和越权访问。
- 根据真实持久化记录计算已打卡天数、当前连续天数、最长连续天数、完成率和最近七天趋势。
- AI 自动拆解学习目标、分析自然语言学习记录并生成反馈；不可用时明确降级到手动流程。
- 计划详情支持自行增减子计划，自动重新分配权重并重算学习进度。
- AI 故障时在记录页保存待分析记录并打卡；恢复后可按原日期补分析，防止重复增加进度，支持导入旧版手动打卡。

子计划管理和故障恢复的设计、接口及验证说明见 [子计划管理与故障记录补分析](docs/子计划管理与故障记录补分析.md)。
首页问候语、打卡目标反馈、表单布局和 AI 学科拆解说明见 [首页表单与学科拆解优化](docs/首页表单与学科拆解优化.md)。

## 技术栈

- 后端：Java 17、Spring Boot 4、Spring Data JPA、MySQL 8、JWT、Swagger/OpenAPI。
- 前端：Vue 3、uni-app、Vite，支持 H5 和移动端构建。
- AI 服务：Python 3、Flask、智谱 GLM API，可选启动。
- 测试：JUnit 5、Mockito、Node.js 契约与核心流程检查、H5 构建验证。

## 项目结构

```text
LifeTrack/
├─ backend/       Spring Boot API、数据库实体和自动化测试
├─ frontend/      uni-app 页面、API 封装和前端验证脚本
├─ AI_Server/     可选 Python AI 中转服务
├─ docs/          阶段验收记录
├─ .env.example   环境变量示例，不包含真实凭据
└─ AGENTS.md      项目范围、任务状态和协作说明
```

## 环境要求

- JDK 17
- MySQL 8.x
- Node.js 20+ 与 npm
- Python 3.8+（仅使用 AI 功能时需要）

## 配置环境变量

仓库不保存真实数据库密码、JWT secret 或模型 API Key。请参考 [.env.example](.env.example)：根目录一键启动脚本会自动加载本地 `.env`；手动分模块启动时，也可以在启动终端中设置相同的环境变量。

PowerShell 示例：

```powershell
$env:DB_USERNAME = "your_mysql_username"
$env:DB_PASSWORD = "your_mysql_password"
$env:JWT_SECRET = "replace_with_a_random_secret_of_at_least_32_characters"
```

Bash 示例：

```bash
export DB_USERNAME="your_mysql_username"
export DB_PASSWORD="your_mysql_password"
export JWT_SECRET="replace_with_a_random_secret_of_at_least_32_characters"
```

可选变量：

| 变量 | 默认值或用途 |
| --- | --- |
| `DB_URL` | 默认连接本机 `lifetrack` 数据库，业务时区为 `Asia/Shanghai` |
| `JWT_EXPIRATION` | 默认 `86400000` 毫秒 |
| `ZHIPU_API_KEY` | 启动 AI 模型调用时必须设置 |
| `ZHIPU_API_URL` | 默认智谱 Chat Completions 地址 |
| `ZHIPU_MODEL` | 默认 `glm-4-flash` |
| `AI_TEMPERATURE` | 默认 `0.3` |

生成 JWT secret 的一种方式：

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

## 数据库初始化

1. 创建数据库：

```sql
CREATE DATABASE lifetrack CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 可执行 `backend/sql/init_db.sql` 初始化已有结构。开发环境同时启用了 JPA `ddl-auto: update`，会补齐当前实体对应的字段和表。
3. 设置 `DB_USERNAME`、`DB_PASSWORD`；数据库不在本机时同时设置 `DB_URL`。

## 启动顺序

### 推荐：Windows 本地一键启动

首次使用时，复制 `.env.example` 为 `.env`，并至少填写 MySQL 密码。`.env` 已被 Git 忽略，不会随代码提交：

```powershell
Copy-Item .env.example .env
notepad .env
```

确认 MySQL 已启动且 `lifetrack` 数据库存在后，在项目根目录运行：

```powershell
.\start-local.ps1
```

基础计划和打卡无需 AI。需要同时启动可选 AI 服务时使用：

```powershell
.\start-local.ps1 -WithAI
```

启动脚本会重新编译后端，依次检查后端 OpenAPI、前端首页和 AI `/health` 的 HTTP 响应，并在失败时清理本次启动的进程树。日志和进程记录保存在 `.lifetrack-run`。AI 健康检查表示服务已就绪；实际模型调用仍取决于网络、密钥和模型服务。

当前 AI 调用链为前端 → Spring Boot → Python Flask → 智谱 GLM，默认模型 `glm-4-flash`。默认超时分别为模型连接 3 秒、模型读取 30 秒、后端读取 40 秒、前端 AI 创建请求 50 秒；后端连接本地 AI 服务的超时为 2 秒。可通过 `.env.example` 中的 AI 配置调整，调整时应保持外层等待时间大于内层。

任务列表支持“批量管理”，可选择多个任务或全选当前筛选列表后删除，每次最多 100 项。删除包含关联打卡和学习记录，提交前会确认；后端会先检查整批任务归属，再在同一事务内删除。任务按进行中、未开始、已完成、已取消、已过期排序，并使用不同状态颜色。

停止全部服务：

```powershell
.\stop-local.ps1
```

脚本会自动加载 `.env`、启动后端和前端，并在首次启动前端时安装依赖。运行日志保存在本地 `.lifetrack-run` 目录，该目录不会提交到 Git。

以下为分模块手动启动方式。

### 1. 启动 MySQL

确认 `lifetrack` 数据库存在，并且环境变量中的账号具有该数据库的读写和建表权限。

### 2. 启动后端

Windows：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

macOS/Linux：

```bash
cd backend
./mvnw spring-boot:run
```

后端默认地址：`http://localhost:8081`

Swagger：`http://localhost:8081/swagger-ui.html`

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev:h5
```

H5 默认地址：`http://localhost:5173/#/`

局域网手机或 Android 模拟器联调：

```bash
npm run dev:h5:lan
```

Android 模拟器访问 `http://10.0.2.2:5173/#/`；真机访问电脑的局域网 IP。

### 4. 可选：启动 AI 服务

基础计划和打卡无需启动该服务。需要 AI 功能时：

```powershell
$env:ZHIPU_API_KEY = "your_zhipu_api_key"
cd AI_Server
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install flask requests
python ai_server.py
```

AI 服务默认地址：`http://127.0.0.1:5000`。如果服务或模型接口不可用，AI 拆解会返回明确提示，前端自动切换到手动创建计划。

## 测试账号

以下方式只用于本地课程演示环境：

- 登录页“开发环境一键登录”：后端自动创建或复用开发用户。
- 手机号登录：填写任意测试手机号，开发验证码为 `123456`。

请勿把开发验证码和一键登录机制直接用于生产环境。

## 验证命令

后端测试需要先设置数据库环境变量：

```powershell
cd backend
.\mvnw.cmd test
```

前端契约、核心流程和 H5 构建：

```powershell
cd frontend
npm run verify
```

后端启动后可执行真实数据库验收：

```powershell
cd backend
.\scripts\verify-p1-acceptance.ps1
```

脚本会创建临时计划并完成编辑、连续打卡、重复打卡拒绝、日期越界、历史查询和统计同步验证，最后清理本次创建的数据。

## AI 使用情况

项目内的运行时 AI 功能包括：

- 将学习目标拆解为带权重的学习步骤。
- 分析自然语言学习记录并匹配相关步骤。
- 生成阶段反馈、激励语和情绪反馈。

课程开发过程中的 AI 辅助记录、开发设计文档、演示脚本和演示视频将在人工测试稳定后补充。AI 运行时功能与计划、打卡、统计核心链路隔离。

## 当前完成情况

- P0 核心功能闭环：完成。
- P1 质量与验收：完成。
- P2：README、安全配置示例和测试账号说明已完成；开发设计文档、AI 辅助开发记录、演示脚本和演示视频待人工测试稳定后制作。
- P3 可选增强：不属于课程硬性验收要求。

## 安全提醒

- 不要提交 `.env`、`.env.local`、真实数据库密码、JWT secret 或模型 API Key。
- 如果真实凭据曾经进入 Git 历史，仅修改当前文件并不能让旧凭据失效，应立即在对应平台轮换凭据。
- 生产环境应关闭开发登录和固定验证码，并使用数据库迁移工具替代 `ddl-auto: update`。
