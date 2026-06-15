# LifeTrack AI

LifeTrack AI 是一款面向大学生的智能任务追踪与压力管理工具。通过人工智能技术，将模糊的自然语言输入转化为结构化的成长轨迹，帮助用户量化努力，缓解焦虑。

## 🏗 项目架构

本项目采用前后端分离及微服务化思想（AI 逻辑独立为 Python 服务）：

- **`backend`**: 基于 Spring Boot 3.x 的核心业务 API 服务。
- **`frontend`**: 基于 uni-app + Vue 3 的多端适配前端。
- **`AI_Server`**: 基于 Flask 的 AI 逻辑处理中转服务，对接智谱 GLM-4 大模型。

---

## 🚀 快速启动

按照以下顺序启动各模块以确保系统正常运行。

### 1. 环境准备
- **JDK 17+**
- **Node.js** (建议 LTS 版本)
- **Python 3.8+**
- **MySQL 8.0**

### 2. 数据库配置
1. 创建名为 `lifetrack` 的数据库。
2. 运行 `backend/sql/init_db.sql` 脚本初始化表结构。
3. 检查 `backend/src/main/resources/application-dev.yml` 中的数据库账号密码（默认为 `root` / `Dyx121109140@`）。

### 3. 启动 AI 服务 (AI Server)
AI 服务负责语义解析与任务拆解。
```bash
cd AI_Server
# 建议创建虚拟环境
python -m venv venv
source venv/bin/activate  # Windows 使用 venv\Scripts\activate
pip install flask requests
python ai_server.py
```
*服务将运行在 `http://127.0.0.1:5000`*

### 4. 启动后端 (Backend)
```bash
cd backend
# 使用 Maven 启动
./mvnw spring-boot:run
```
*服务将运行在 `http://127.0.0.1:8081`*
- **Swagger 文档**: `http://127.0.0.1:8081/swagger-ui.html`

### 5. 启动前端 (Frontend)
```bash
cd frontend
npm install
npm run dev:h5
```
*打开浏览器访问 `http://127.0.0.1:5173`*

---

## 🛠 技术栈

- **后端**: Spring Boot 3, MyBatis Plus, MySQL, JWT.
- **前端**: Vue 3, uni-app, Vite, 玻璃拟态 UI 设计.
- **AI**: Python Flask, 智谱 GLM-4 API.

## 📝 项目文档
- [项目详细介绍文档](Project_Introduction.md)
- [后端开发指南](backend/README.md)
- [前端开发指南](frontend/README.md)
- [接口联调说明](frontend/FRONTEND_BACKEND_INTEGRATION.md)
