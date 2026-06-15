# LifeTrack AI Server

这是 LifeTrack 项目的 AI 中转服务，基于 Python Flask 构建。它负责对接大语言模型（GLM-4），处理语义解析、任务拆解、行为匹配等核心 AI 逻辑。

## 🛠 功能
- **任务拆解**: 将用户目标转化为带权重的子任务。
- **意图识别**: 判断用户输入是创建目标、记录进度还是日常闲聊。
- **进度判定**: 分析用户行为描述，自动计算任务贡献度。
- **情绪反馈**: 根据用户状态生成个性化的激励语和成长报告。

## 🚀 启动指南

### 1. 安装依赖
确保已安装 Python 3.8+。
```bash
# 安装 Flask 和 requests 库
pip install flask requests
```

### 2. 配置文件
AI 密钥已硬编码在 `ai_server.py` 中（智谱 AI）。如需更换模型或密钥，请修改 `ai_server.py` 开头的配置项。

### 3. 运行服务
```bash
python ai_server.py
```
默认运行在 `http://127.0.0.1:5000`。

## 接口说明
- `POST /ai/deconstruct`: 任务拆解
- `POST /ai/intent`: 意图识别
- `POST /ai/progress-judge`: 进度判定
- `POST /ai/motivation`: 激励文案生成
- `POST /ai/report`: 周报生成
- `POST /ai/mood-quote`: 情绪寄语获取
