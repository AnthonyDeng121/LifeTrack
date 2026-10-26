# LifeTrack AI Server

可选的 Flask AI 中转服务，用于计划拆解、自然语言进度识别、反馈报告和激励文案。基础计划与打卡功能不依赖本服务。

## 安装与配置

```powershell
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:ZHIPU_API_KEY = "your_zhipu_api_key"
python ai_server.py
```

`requirements.txt` 固定了已验证的直接和间接依赖版本；当前验证环境为 Python 3.12。本地启动脚本也按此文件安装。

可选变量：

- `ZHIPU_API_URL`：默认智谱 Chat Completions 地址。
- `ZHIPU_MODEL`：默认 `glm-4-flash`。
- `AI_TEMPERATURE`：默认 `0.3`。

真实 API Key 只能通过环境变量提供，不要写入源码、README 或 Git 历史。完整变量示例见根目录 `.env.example`。

服务默认运行在 `http://127.0.0.1:5000`。未设置 `ZHIPU_API_KEY` 时服务可以启动，但模型调用会返回失败，Java 后端会将 AI 功能降级并保持核心业务可用。

## 接口

- `POST /ai/deconstruct`
- `POST /ai/intent`
- `POST /ai/progress-judge`
- `POST /ai/motivation`
- `POST /ai/report`
- `POST /ai/mood-quote`
