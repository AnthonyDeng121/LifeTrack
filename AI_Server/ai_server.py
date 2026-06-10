from flask import Flask, request, jsonify
import requests
import json

# 智谱大模型基础配置
API_KEY = "bbd7cf8142db49f89faa156bc4f79bb3.r1f6yVwIycot7rIM"
URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
MODEL = "glm-4-flash"
TEMPERATURE = 0.3

HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}

# ===================== 1. 任务拆解 Prompt(goal_breaker.txt) =====================
GOAL_BREAKER_PROMPT = """
你是一个任务规划专家。用户会输入一个或多个目标，请按以下规则处理。
【安全优先】
- 先判断：涉及违法、暴力、危险、违禁内容，直接拒绝，**不输出JSON**。
- 拒绝话术：先明确告知不可如此，再给约50字温和心理疏导，纯文本，不要解释、不要markdown、不要```。
【步骤1：识别目标数量】
判断用户输入中包含几个独立目标。规则：
- 出现“同时”、“另外”、“还有”、“并且”等连接词，通常为多个目标
- 语义明显不同（如学习和健身），拆分为不同目标
- 高度相关（如“学Java + 做项目”），合并为一个目标
【步骤2：逐个拆解目标】
每个目标拆解为子任务，数量根据复杂度动态决定：
- 简单目标：2~3个子任务
- 普通目标：4~6个子任务
- 复杂目标：6~8个子任务
【每个子任务的字段】
- step：任务描述（15字以内，动词开头，有明确完成标准；**禁止写“完成XX目标”这类收尾空话，避免写“每天”等高频重复动作**）
- weight：权重（0.00~1.00，保留两位小数，同目标内权重之和严格等于1.00）
【⚠️ 强制校验规则（必须遵守）】
1. 权重分配逻辑合理：核心执行任务权重最高（≥0.25），计划/准备类权重偏低（≤0.15）
2. 严格自检：输出前必须计算权重总和，**不等于1.00时自动重新分配**，确保精准等于1.00
【禁止事项】
- 禁止过于宽泛（如“学会Java”）
- 禁止过于琐碎（如“打开电脑”）
- 正常输入：仅输出JSON，禁止输出JSON以外的任何文字
- 违规输入：不输出JSON，输出拒绝+疏导文本
【输出格式】
正常输入：
{
  "goals": [
    {
      "goal_id": 1,
      "title": "目标名称",
      "sub_tasks": [
        {"step": "任务1", "weight": 0.10},
        {"step": "任务2", "weight": 0.25}
      ]
    }
  ]
}
违规输入示例：
你不可如此，此类行为违法且危害极大。请放下危险想法，专注正向目标，生活有很多温暖，好好爱自己，慢慢来一切都会好。
【示例1：单目标】
用户输入：本学期学会Spring Boot
输出：
{
  "goals": [
    {
      "goal_id": 1,
      "title": "本学期学会Spring Boot",
      "sub_tasks": [
        {"step": "学习基础语法与注解", "weight": 0.15},
        {"step": "完成Spring MVC网课", "weight": 0.25},
        {"step": "编写RestController Demo", "weight": 0.30},
        {"step": "完成完整实战项目", "weight": 0.30}
      ]
    }
  ]
}
【示例2：多目标】
用户输入：考研上岸，同时坚持每周健身三次
输出：
{
  "goals": [
    {
      "goal_id": 1,
      "title": "考研上岸",
      "sub_tasks": [
        {"step": "完成数学一轮复习", "weight": 0.25},
        {"step": "完成英语真题10套", "weight": 0.25},
        {"step": "完成专业课知识点梳理", "weight": 0.30},
        {"step": "完成冲刺模考5次", "weight": 0.20}
      ]
    },
    {
      "goal_id": 2,
      "title": "每周健身三次",
      "sub_tasks": [
        {"step": "制定健身计划", "weight": 0.15},
        {"step": "第一周完成3次健身", "weight": 0.25},
        {"step": "第二周完成3次健身", "weight": 0.25},
        {"step": "第三周完成3次健身", "weight": 0.20},
        {"step": "第四周完成3次健身并复盘", "weight": 0.15}
      ]
    }
  ]
}
【示例3：习惯类目标】
用户输入：每天背20个单词
输出：
{
  "goals": [
    {
      "goal_id": 1,
      "title": "每天背20个单词",
      "sub_tasks": [
        {"step": "整理核心单词清单", "weight": 0.20},
        {"step": "掌握单词记忆方法", "weight": 0.25},
        {"step": "完成四周单词打卡", "weight": 0.35},
        {"step": "定期复习巩固单词", "weight": 0.20}
      ]
    }
  ]
}
用户输入：{{user_goal}}
输出：
"""

# ===================== 2. 意图识别 Prompt(intent_classifier.txt) =====================
INTENT_PROMPT = """
你是一个意图识别专家。用户会输入一段话，请判断用户的意图是哪一类，只输出纯JSON，不要任何解释、不要markdown、不要```json、不要```、不要换行。
【意图类型】
1. create_goal：用户想设定一个新目标
   - 关键词：我要、我想、打算、计划、目标是、学会、完成、拿到
   - 特征：描述未来想达成的事，没有“今天/昨天/刚刚”等时间词
2. log_progress：用户想记录今天/最近的行为
   - 关键词：今天、刚刚、下午、学了、做了、看了、完成了
   - 特征：描述已经发生的行为，有时间标记或过去时
3. check_progress：用户想查询当前进度
   - 关键词：进度、多少了、完成了多少、还剩什么
4. other：其他（问候、闲聊、无关内容）
【歧义处理】
如果一句话既像目标又像进度（如“背单词”），默认判断为 create_goal。
宁可漏判进度，不可误判目标。
【输出格式】
{"intent": "create_goal" | "log_progress" | "check_progress" | "other"}
【示例】
用户输入：我要学会Spring Boot → {"intent": "create_goal"}
用户输入：我今天学了2小时Spring Boot → {"intent": "log_progress"}
用户输入：考研上岸 → {"intent": "create_goal"}
用户输入：上午看了网课，下午写了代码 → {"intent": "log_progress"}
用户输入：现在进度多少了 → {"intent": "check_progress"}
用户输入：背单词 → {"intent": "create_goal"}
用户输入：你好 → {"intent": "other"}
用户输入：{{user_input}}
输出：
"""

# ===================== 3. 进度判断 Prompt(progress_judge.txt) =====================
PROGRESS_JUDGE_PROMPT = """
你是一个进度判断专家。用户会输入一条日常行为记录，系统会提供当前所有未完成的子任务列表。
【输入格式】
用户行为：{{user_action}}
用户提供时长：{{duration_input}}
子任务列表：{{sub_tasks}}
【输出格式】
{
  "matches": [{"task_id": 数字, "contribution": 小数}],
  "duration_minutes": 数字,
  "is_entertainment": 布尔值,
  "suggested_reply": "字符串"
}
【规则】
1. 精准匹配优先
2. 提取时长：优先从【用户提供时长】中解析分钟数。如果用户提供时长模糊（如“一会儿”），请结合【用户行为】进行【合理解构】：
   - 比如“炒个菜”+“一会儿”约 15-20 分钟
   - "看书"+"1.5小时"= 90 分钟
   - “学了一会”约 15-30 分钟
3. 模糊输入（“学了一会”）→ contribution = 0.05
4. 匹配多个任务时，contribution 总和≤1.0
5. 娱乐行为（打游戏、刷剧）→ is_entertainment = true, matches = []
6. 单次贡献度不超过0.30
7. 只输出纯JSON，严禁输出任何Python代码、解释、markdown、```
【示例1】
用户行为：看了2小时Spring Boot网课
子任务列表：[{"task_id": 1, "step": "学习基础语法"}, {"task_id": 2, "step": "完成Spring MVC网课"}]
输出：
{
  "matches": [{"task_id": 2, "contribution": 0.20}],
  "duration_minutes": 120,
  "is_entertainment": false,
  "suggested_reply": "网课进度+20%，坚持就是胜利！"
}
【示例2】
用户行为：炒了个西红柿炒鸡蛋
子任务列表：[{"task_id": 10, "step": "练习烹饪技巧"}]
输出：
{
  "matches": [{"task_id": 10, "contribution": 0.10}],
  "duration_minutes": 20,
  "is_entertainment": false,
  "suggested_reply": "大厨辛苦了，烹饪进度+10%"
}
【示例3】
用户行为：打了2小时王者荣耀
子任务列表：[{"task_id": 1, "step": "学习基础语法"}]
输出：
{
  "matches": [],
  "is_entertainment": true,
  "suggested_reply": "放松一下，回来继续"
}
用户行为：{{user_action}}
用户提供时长：{{duration_input}}
子任务列表：{{sub_tasks}}
输出：
"""

# ===================== 4. 激励文案 Prompt(prompt_motivation_v0.txt) =====================
MOTIVATION_PROMPT = """
你是大学生专属成长陪伴助手，根据用户今日/本周学习进度、用户名，生成1条个性化激励语，严格遵守以下规则：
1. 必须基于数据：严格根据今日/本周进度百分比进行分析，但不要提到具体进度；
2. 语气温暖、生动有力，40-60字，拒绝空洞鸡汤；
3. 不同进度对应不同风格：
   - 进度≥80%：肯定+鼓励，描述为完成很好
   - 进度50%-79%：鼓励+正向引导，描述为还需努力
   - 进度<50%：温和鼓励+调整建议
   不要说具体数字，不要说打击人的话
4. 只输出1条纯文本激励语，不要解释、不要换行、不要表情符号。
今日进度：{{today_progress}}%
本周进度：{{week_progress}}%
用户名：{{user_name}}
"""

# ===================== 5. 周报总结 Prompt =====================
REPORT_PROMPT = """
你是一个大学生学业分析专家。根据用户过去一周的行为日志，生成一份深度成长报告。
【输入数据】
用户行为日志：{{logs}}
【输出格式】
只输出纯JSON，严禁输出任何Python代码、解释、markdown、```
{
  "title": "本周成长深度报告",
  "ai_summary": "总结用户本周的核心表现、成长点、不足（约100字）",
  "achievement_tags": ["标签1", "标签2"],
  "suggestion": "针对下周的改进建议（约50字）"
}
"""

# 通用工具函数
def clean_output(text):
    """清除markdown代码块标记并提取JSON部分"""
    text = text.replace("```json", "").replace("```", "").strip()
    # 尝试寻找第一个 { 和最后一个 } 之间的内容
    try:
        start = text.find('{')
        end = text.rfind('}')
        if start != -1 and end != -1:
            return text[start:end+1]
    except:
        pass
    return text

def call_llm(prompt):
    """统一调用智谱大模型"""
    payload = {
        "model": MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": TEMPERATURE
    }
    resp = requests.post(URL, json=payload, headers=HEADERS, timeout=20)
    resp.raise_for_status()
    return resp.json()["choices"][0]["message"]["content"]

# 初始化Flask服务
app = Flask(__name__)

# 接口1：任务拆解
@app.route("/ai/deconstruct", methods=["POST"])
def ai_deconstruct():
    req_data = request.get_json()
    user_goal = req_data.get("title", "")
    final_prompt = GOAL_BREAKER_PROMPT.replace("{{user_goal}}", user_goal)
    raw_res = call_llm(final_prompt)
    res_text = clean_output(raw_res)
    try:
        data = json.loads(res_text)
        return jsonify({"code": 200, "msg": "success", "data": data})
    except json.JSONDecodeError:
        return jsonify({"code": 400, "msg": res_text, "data": None})

# 接口2：意图识别
@app.route("/ai/intent", methods=["POST"])
def ai_intent():
    req_data = request.get_json()
    user_input = req_data.get("user_input", "")
    final_prompt = INTENT_PROMPT.replace("{{user_input}}", user_input)
    raw_res = call_llm(final_prompt)
    res_text = clean_output(raw_res)
    return jsonify({"code": 200, "msg": "success", "data": res_text})

# 接口3：进度判断/行为匹配
@app.route("/ai/progress-judge", methods=["POST"])
def ai_progress_judge():
    try:
        req_data = request.get_json()
        user_action = req_data.get("user_action", "")
        duration_input = req_data.get("duration_input", "")
        sub_tasks = req_data.get("sub_tasks", "")
        final_prompt = PROGRESS_JUDGE_PROMPT\
            .replace("{{user_action}}", user_action)\
            .replace("{{duration_input}}", duration_input)\
            .replace("{{sub_tasks}}", sub_tasks)
        raw_res = call_llm(final_prompt)
        res_text = clean_output(raw_res)
        return jsonify({"code": 200, "msg": "success", "data": res_text})
    except Exception as e:
        print(f"Error in progress-judge: {e}")
        return jsonify({"code": 500, "msg": str(e), "data": None})

# 接口4：生成激励文案
@app.route("/ai/motivation", methods=["POST"])
def ai_motivation():
    req_data = request.get_json()
    today = req_data.get("today_progress", "")
    week = req_data.get("week_progress", "")
    username = req_data.get("user_name", "")
    final_prompt = MOTIVATION_PROMPT\
        .replace("{{today_progress}}", str(today))\
        .replace("{{week_progress}}", str(week))\
        .replace("{{user_name}}", username)
    raw_res = call_llm(final_prompt)
    res_text = clean_output(raw_res)
    return jsonify({"code": 200, "msg": "success", "data": res_text})

# 接口5：生成周报
@app.route("/ai/report", methods=["POST"])
def ai_report():
    req_data = request.get_json()
    logs = req_data.get("logs", "")
    final_prompt = REPORT_PROMPT.replace("{{logs}}", str(logs))
    raw_res = call_llm(final_prompt)
    res_text = clean_output(raw_res)
    try:
        data = json.loads(res_text)
        return jsonify({"code": 200, "msg": "success", "data": data})
    except json.JSONDecodeError:
        return jsonify({"code": 400, "msg": "AI生成报告失败", "data": None})

# 接口6：调整策略
@app.route("/ai/adjust-strategy", methods=["POST"])
def ai_adjust_strategy():
    req_data = request.get_json()
    user_id = req_data.get("user_id")
    anxiety_level = req_data.get("anxiety_level")
    # 这里可以根据焦虑等级调整全局参数或返回建议，目前先做记录
    print(f"User {user_id} mood adjusted to {anxiety_level}")
    return jsonify({"code": 200, "msg": "AI策略已根据情绪调整"})

# 接口7：获取情绪寄语
@app.route("/ai/mood-quote", methods=["POST"])
def ai_mood_quote():
    req_data = request.get_json()
    anxiety_level = req_data.get("anxiety_level", 5)
    username = req_data.get("username", "同学")
    
    prompt = f"""
    你是一个温暖的成长陪伴教练。用户【{username}】现在的焦虑等级是【{anxiety_level}/10】。
    请根据这个焦虑等级，给用户写一句极其简短（15字以内）、个性化、有温度的鼓励或建议。
    要求：
    1. 严禁使用空洞的鸡汤。
    2. 如果焦虑值高(>7)，语气要极其温柔且建议休息。
    3. 如果焦虑值低(<4)，语气要活泼且鼓励进取。
    4. 只输出这句寄语，不要任何其他字符。
    """
    raw_res = call_llm(prompt)
    return jsonify({"code": 200, "msg": "success", "data": raw_res.strip()})

if __name__ == "__main__":
    # 本地运行，端口5000
    app.run(host="127.0.0.1", port=5000, debug=True)