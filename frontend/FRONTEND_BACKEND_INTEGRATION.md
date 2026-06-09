# LifeTrack 前后端联调说明

本文档用于前端与后端联调。前端项目位于 `frontend`，当前使用 uni-app + Vue3。

## 1. 前端运行

```bash
cd frontend
npm install
npm run dev:h5:lan
```

浏览器访问：

```text
http://127.0.0.1:5173/#/
```

Android 模拟器访问：

```text
http://10.0.2.2:5173/#/
```

## 2. 后端地址约定

后端当前 dev 端口来自 `backend/src/main/resources/application-dev.yml`：

```text
8081
```

前端会自动选择 API 地址：

| 场景 | 前端页面地址 | API 前缀 |
| --- | --- | --- |
| Mac 本机浏览器 | `127.0.0.1:5173` 或 `localhost:5173` | `http://localhost:8081/api/v1` |
| Android 模拟器 Chrome | `10.0.2.2:5173` | `http://10.0.2.2:8081/api/v1` |
| 局域网预览 | `192.168.x.x:5173` | `http://192.168.x.x:8081/api/v1` |

如需临时改地址，可在浏览器 storage 中写入：

```js
uni.setStorageSync("lifetrack_api_base_url", "http://后端地址:8081/api/v1")
```

## 3. 统一响应与鉴权

后端统一响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

前端只把 `code === 200` 视为成功。后端返回业务错误时，前端会展示 `message`，不会再用 mock 数据覆盖真实错误。

除认证接口外，后端接口需要：

```http
Authorization: Bearer <token>
```

前端登录成功后会保存 `token`，后续请求自动带上。

## 4. 页面与接口映射

| 页面 | 前端入口 | 后端接口 |
| --- | --- | --- |
| 登录页 | `/pages/login/login` | `POST /auth/login/dev`、`POST /auth/login/phone` |
| 首页 | `/pages/dashboard/dashboard` | `GET /dashboard/stats`、`GET /feedback/report`、`GET /tasks` |
| 任务页 | `/pages/tasks/tasks` | `GET /tasks`、`POST /tasks/deconstruct` |
| 任务详情 | `/pages/task-detail/task-detail` | `GET /tasks/{taskId}/subtasks`、`PUT /tasks/subtasks/{subTaskId}/complete` |
| 记录页 | `/pages/record/record` | `POST /actions/sync`、`GET /actions/history?page=0&size=20`、`DELETE /actions/{actionId}`、`POST /feedback/mood` |
| 我的页 | `/pages/profile/profile` | `GET /feedback/report`、`GET /dashboard/stats` |

## 5. 任务创建逻辑

任务页弹窗有两种模式：

| 模式 | 用户行为 | 当前后端接口 |
| --- | --- | --- |
| AI 拆解 | 用户输入长期目标，后端 AI 拆解子任务 | `POST /tasks/deconstruct` |
| 手动添加 | 用户手动输入任务标题和分类 | 仍走 `POST /tasks/deconstruct` |

原因：当前后端没有单独的纯手动 `POST /tasks` 接口。为了不绕开数据库结构，手动添加也提交给 `deconstruct`，由后端保存主任务并生成子任务。

请求体：

```json
{
  "title": "学习 Spring Boot",
  "category": "学习"
}
```

## 6. 字段与枚举约定

任务分类必须使用后端 `Task.Category` 枚举：

```text
学习 / 娱乐 / 休息 / 运动 / 琐事
```

前端已经移除了不在后端枚举内的 `项目 / 健康 / 生活`，避免 `Task.Category.valueOf()` 报错。

进度字段约定：

| 字段 | 后端返回单位 | 前端处理 |
| --- | --- | --- |
| `TaskListResponse.totalProgress` | 0-100 | 直接显示百分比 |
| `SubTaskListResponse.weight` | 0-100 | 直接显示百分比 |
| `SubTaskListResponse.currentProgress` | 0-100 | 直接显示百分比 |
| `ActionSyncResponse.increment` | 0-100 | 直接显示百分比 |
| `ActionHistoryResponse.contribution` | 0-100 | 直接显示百分比 |
| `TaskDeconstructResponse.subTasks[].weight` | 0-1 | 前端作为后端原始拆解权重展示 |

分页约定：

```text
GET /actions/history?page=0&size=20
```

Spring Data `Pageable` 默认从 `page=0` 开始，前端已按 0 基分页请求。

## 7. Mock 规则

前端现在只在网络失败或后端未启动时使用 mock 数据兜底，方便 UI 预览。

如果后端正常返回：

```json
{ "code": 400, "message": "请先创建任务再进行同步", "data": null }
```

前端会直接展示错误，不会伪装成成功。

## 8. 联调建议流程

1. 后端启动在 `8081`。
2. 前端用 `npm run dev:h5:lan` 启动在 `5173`。
3. 前端先点“开发环境一键登录”，拿到 token。
4. 进入任务页，新建一个任务。
5. 进入记录页，提交自然语言行为。
6. 检查数据库：
   - `tasks` 是否新增任务
   - `sub_tasks` 是否新增子任务
   - `action_log` 是否新增行为记录
   - 子任务与主任务进度是否变化

## 9. 后端补充点及故障排查

- 如果允许，可以补一个纯手动新建任务接口，例如 `POST /api/v1/tasks`，这样前端“手动添加”和“AI 拆解”能完全分开。
- H5 联调如果出现跨域问题，可以在 `WebMvcConfig` 里加 CORS 配置，允许 `http://localhost:5173`、`http://127.0.0.1:5173`、`http://10.0.2.2:5173`。
- 目前业务异常返回的是统一 JSON，这对前端已经够用；如果后续要更标准，也可以让 401/400 对应真实 HTTP 状态码，方便浏览器调试。
