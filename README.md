# LifeTrack AI 学习计划与智能打卡系统

LifeTrack 是“企业级 AI 软件设计与开发”课程综合实践项目，对应“学习计划与打卡系统”选题。系统以真实的计划、每日打卡和统计闭环为核心，并提供可选的 AI 计划拆解、自然语言学习记录和反馈报告。

AI 服务不是基础业务的运行前提。AI 不可用时，用户仍可手动创建、查看、编辑和删除计划，完成每日打卡并查看历史和统计。

## 核心功能

- 学习计划新增、详情、编辑、删除，以及按状态和日期筛选。
- 设置开始日期、结束日期、目标打卡天数和计划状态。
- 未开始、进行中、已完成、已取消和已过期状态及合法流转。
- 选择计划后一键完成今日打卡，不填写备注或时长；打卡仅用于天数与连续天数统计。
- 首条有效学习记录自动关联当日打卡；已打卡时显示“今日已打卡”，学习记录可同日多次追加。
- 按计划和日期查询打卡历史。
- 计划列表支持周期范围与状态组合筛选；学习历史每页20条，支持上一页和下一页，历史与统计分别处理错误。
- 数据库与服务层共同阻止同一用户、同一计划、同一天重复打卡。
- 处理非法周期、未来或周期外打卡、非法状态、空输入和越权访问。
- 根据真实持久化记录计算已打卡天数、当前连续天数、最长连续天数、完成率和最近七天趋势。
- AI 自动拆解学习目标、分析自然语言学习记录并生成反馈；不可用时明确降级到手动流程。
- 未开始或进行中的计划支持增减子计划，自动重新分配权重并重算学习进度。
- 已完成计划不可回退或重新打开；修改周期涉及删除周期外打卡时，必须预览影响并明确确认。
- 学习记录先选计划，明确填写整数小时和分钟（总计1～1440分钟）。原文先持久化，分析成功才累计时长并自动打卡；失败记录保留且不计时，可按原日期重新分析，成功只能应用一次。
- 计划编辑携带读取时的版本号，旧窗口保存返回409；子步骤重新分配后每项权重至少1%，新增和编辑标题统一限制100字符。

子计划管理和故障恢复的设计、接口及验证说明见 [子计划管理与故障记录补分析](docs/子计划管理与故障记录补分析.md)。
首页问候语、打卡目标反馈、表单布局和 AI 学科拆解说明见 [首页表单与学科拆解优化](docs/首页表单与学科拆解优化.md)。
计划终态、周期修改确认、权重校验和本次回归记录见 [计划业务规则修复与验收记录](docs/计划业务规则修复与验收记录.md)。
独立打卡、日期筛选、历史分页与故障隔离见 [核心功能与用户操作修复记录](docs/核心功能与用户操作修复记录.md)。
当前统计口径、一键打卡、固定分页和焦虑值页面迁移见 [统计口径与打卡交互调整](docs/统计口径与打卡交互调整.md)。

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
├─ docs/          设计、AI过程、演示脚本与验收证据
├─ .env.example   环境变量示例，不包含真实凭据
└─ AGENTS.md      项目范围、任务状态和协作说明
```

## 环境要求

- JDK 17
- MySQL 8.x
- Node.js 20+ 与 npm
- Python 3.12（已验证版本，AI 服务与完整验收需要；纯核心业务启动可不装）

## 配置环境变量

仓库不保存真实数据库密码、JWT secret 或模型 API Key。请参考 [.env.example](.env.example)：根目录一键启动脚本会自动加载本地 `.env`；手动分模块启动时，也可以在启动终端中设置相同的环境变量。

PowerShell 示例：

```powershell
$env:DB_USERNAME = "your_mysql_username"
$env:DB_PASSWORD = "your_mysql_password"
$env:JWT_SECRET = "replace_with_a_random_secret_of_at_least_32_characters"
$env:SPRING_PROFILES_ACTIVE = "dev"
```

Bash 示例：

```bash
export DB_USERNAME="your_mysql_username"
export DB_PASSWORD="your_mysql_password"
export JWT_SECRET="replace_with_a_random_secret_of_at_least_32_characters"
export SPRING_PROFILES_ACTIVE="dev"
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

1. 安装并启动 MySQL 8.0.16 或更高版本，在 MySQL 客户端执行完整总脚本（无需预先创建数据库）：

```sql
SOURCE D:/0Desktop2/项目/LifeTrack/backend/sql/init_db.sql;
```

2. 脚本创建 `lifetrack` 及全部业务表、索引、外键和 CHECK 约束，不含删除表操作。旧库首次接管前先备份，再执行同一脚本对齐结构；若旧数据违反约束，停止并人工核实，不能删除数据绕过。Flyway 接管后不再执行总脚本。
3. 设置 `DB_USERNAME`、`DB_PASSWORD`；数据库不在本机时同时设置 `DB_URL`。脚本中的数据库名应与连接地址一致。
4. 完成总脚本后，**仅首次启动**设置 `$env:FLYWAY_ADOPT_BASELINE="true"`。应用检查基线所需约束后登记版本 1；启动成功后执行 `Remove-Item Env:FLYWAY_ADOPT_BASELINE`。不要将它长期写入 `.env`。
5. 本次所有 DDL 都在总脚本内，没有 V1 增量迁移。此后新增 `backend/src/main/resources/db/migration/V2__描述.sql` 等版本脚本，启动自动校验和执行。所有环境的 Hibernate 均只 `validate`，不再自动改表；Flyway `clean` 禁用。

数据库与并发实现、验收记录见[数据库、并发与工程质量修复](docs/数据库并发与工程质量修复.md)。

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
pip install -r requirements.txt
python ai_server.py
```

AI 服务默认地址：`http://127.0.0.1:5000`。如果服务或模型接口不可用，AI 拆解会返回明确提示，前端自动切换到手动创建计划。

## 测试账号

以下方式只用于本地课程演示环境：

- 显式 `dev` 环境下，登录页“开发环境一键登录”创建或复用开发用户。
- `dev` 是整台后端服务的运行环境，适用于连接它的所有开发/演示用户，不需要逐用户启用。根目录 `.env` 填写 `SPRING_PROFILES_ACTIVE=dev`，本地脚本会读取；未填写时脚本也会显式选择 `dev`。手动启动可在 `backend` 执行 `$env:SPRING_PROFILES_ACTIVE="dev"`，再执行 `.\mvnw.cmd spring-boot:run`；IDE 的 Active profiles 只填 `dev`。已有进程须重启才生效。
- 不同演示用户使用不同测试手机号即可保留各自的数据；一键登录复用的是同一个开发账号。
- 演示手机号登录需有效的11位测试手机号和6位验证码；点击“查看演示验证码”获取当前开发配置，不会发送真实短信。
- `AUTH_DEV_LOGIN_ENABLED`、`AUTH_DEMO_PHONE_ENABLED` 分别控制这两个入口。开发环境默认开启，可设为 `false` 关闭。
- 无环境配置、`prod`、`test` 或混合环境中，两个入口始终关闭，即使误设开关为 `true` 也不能绕过。基础配置不再自动选择开发环境，本地启动脚本会显式选择 `dev`。
- 微信登录已删除；历史用户与业务数据不作破坏性清理。

请勿把开发验证码和一键登录机制直接用于生产环境。

当前项目未接入真实短信服务；非开发环境不提供演示身份认证。异常响应保留 `code/message/data` 格式，并同步返回实际 HTTP 400、401、403、404、409、500 等状态。认证配置与验收说明见[异常处理与开发认证修复](docs/异常处理与开发认证修复.md)。

## 验证命令

推荐在根目录执行完整、可复现的隔离验收（需要 MySQL 客户端、JDK、Node 和已安装依赖的 AI_Server venv）：

```powershell
.\verify-course.ps1
```

脚本从总 SQL 创建全新的 `lifetrack_test_*` 数据库，运行后端、前端、Python 和验收工具测试，最后清理临时库。凭据读取本机 `.env` 的数据库配置，也可单独设置 `TEST_DB_USERNAME/TEST_DB_PASSWORD`；需要创建和清理测试库的权限。原始日志留在被忽略的 `.verification/`，脱敏结果、逐项用例和源码指纹写入 `docs/verification/`。

所有 Spring 集成测试都拒绝连接普通 `lifetrack` 数据库。单独运行 `mvnw.cmd test` 时必须已准备好总脚本结构并设置 `TEST_DB_URL`（库名必须以 `lifetrack_test_` 开头）、`TEST_DB_USERNAME`、`TEST_DB_PASSWORD`。日常服务继续使用 `DB_*`，不受影响。

前端声明检查、带桩的页面逻辑测试和 H5 构建可单独执行：

```powershell
cd frontend
npm run verify
```

完整验收另含“原始前端 API/http 模块 → 真实 Spring Boot HTTP → MySQL”的契约测试；仅替换 uni 平台传输桥，响应不做 Mock。它仍不等于浏览器点击测试。

后端启动后可执行现场 HTTP 验收：

```powershell
cd backend
.\scripts\verify-p1-acceptance.ps1
```

脚本核对真实 HTTP 状态与 JSON 业务码，可传 `-Token` 使用专用验收账号；未传时使用共享开发账号，仅清理本次临时计划。其历史数量来自 API，不冒充直接 SQL 证据。全局日期增量取实际值，不固定假设增加三天。直接 SQL 的证据由隔离集成测试提供。

测试层级、历史记录纠正及最新证据见[测试与验收可信度整改](docs/测试与验收可信度整改.md)和[验收证据索引](docs/verification/README.md)。

## AI 使用情况

项目内的运行时 AI 功能包括：

- 将学习目标拆解为带权重的学习步骤：先预览，再填写调整建议重新生成，确认后保存；可自动决定步骤数或选择2至10步。
- 分析自然语言学习记录并匹配相关步骤。
- 生成阶段反馈、激励语和情绪反馈。

学习进度与打卡目标达成率独立：学习进度为子计划加权进度，打卡达成率为真实打卡日数除以目标天数（最高100%）。打卡达标不会自动完成学习；学完可以提前结束计划，保留尚未达标的真实打卡率，已完成计划不再新增打卡。

首页只按“已完成计划数 / 全部未取消计划数”计算学习计划完成率，未开始、进行中和已过期计划均计入分母。单个计划详情保留独立的打卡目标达成率，首页不再汇总目标天数计算百分比。全局打卡天数和连续天数按上海业务日期去重，同一天多个计划只算一天。

取消计划保留全部打卡和学习历史，可以继续查询；但不计入看板完成率、打卡天数、连续天数、趋势、学习时长和进度增量，AI反馈的数据范围保持一致。焦虑值入口已移到个人页，当前功能保持原样，不影响学习和打卡规则。

自然语言记录只推进明确匹配的步骤，时长使用用户明确填写的分钟数，不采用模型估算值。涉及多个步骤时，每项必须有用户原文中独立且不重叠的学习事实；笼统笔记、重复证据或不明范围不会整批增加进度，原记录保留为未分析成功且不计时。真实回归命令：`node frontend/scripts/verify-matching-live.mjs`（需要三端和AI密钥，会清理临时计划）。

AI普通投入估算最多推进至子计划95%，达到上限仍可继续保存有效学习记录和打卡；100%需要记录包含具体完成产出与验收事实，模型提供用户原文证据并由后端校验，或由用户在详情页手动确认。判断仍基于用户自述，不代表客观证明已掌握知识；完成或取消计划之前可撤销错误记录，进入终态后冻结学习进度与记录，不能通过撤销、增减步骤或补分析重新打开。

真实模型闭环验证（会创建独立测试账号、临时计划并调用模型，最后删除临时计划）：`node frontend/scripts/verify-ai-live.mjs`。需先启动三端并配置AI密钥。

课程开发过程中的 AI 辅助记录、开发设计文档、需求验收对照和演示脚本已整理，见[课程提交材料索引](docs/交付材料索引.md)。AI 运行时功能与计划、打卡、统计核心链路分开设计；视频由提交人稍后录制。

## 课程交付材料

- [开发设计文档](docs/delivery/开发设计文档.md)：架构、模块、数据模型、流程、规则、接口与测试。
- [AI 辅助开发记录](docs/delivery/AI辅助开发记录.md)：真实 Prompt 节选、采纳内容、修改与证据。
- [需求与验收对照表](docs/delivery/需求与验收对照表.md)：课程要求与实现、验证的映射。
- [演示脚本](docs/delivery/演示脚本.md)：正常业务、至少四类异常、AI 降级与录制自查。
- [提交清单与使用说明](docs/delivery/提交清单与使用说明.md)：新机器部署、提交范围与本人待办。

视频暂未录制。姓名、学号、班级、实际分工和原始对话附件由提交人核实补充；技术材料不虚构这些信息。文档采用 Markdown，若教师另有 Word 模板则按模板排版。

## 当前完成情况

- P0 核心功能闭环：完成。
- P1 质量与验收：完成。
- P2：README、安全配置、测试账号说明、开发设计、AI 辅助过程、需求对照及演示脚本已完成；视频和提交人身份、分工等信息待本人完成。
- P3 可选增强：不属于课程硬性验收要求。

## 安全提醒

- 不要提交 `.env`、`.env.local`、真实数据库密码、JWT secret 或模型 API Key。
- 如果真实凭据曾经进入 Git 历史，仅修改当前文件并不能让旧凭据失效，应立即在对应平台轮换凭据。
- 开发和演示服务显式选择 `dev`；数据库已统一由总脚本初始化、Flyway 接管后续迁移，Hibernate 仅校验结构。
