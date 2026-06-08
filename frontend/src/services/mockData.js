export const mockUser = {
  token: "dev-token",
  userId: 1,
  username: "林同学",
  avatar: "",
};

export const mockDashboardStats = {
  todayTotalProgress: 15.5,
  dailyQuote: "你今天已经积累了 15.5% 的进度，核心接口已调通，晚上可以安心休息一下了。",
  timeDistribution: [
    { name: "学习", minutes: 120, percentage: 45, behaviors: [{ actionName: "学习 Spring Boot 注解", minutes: 45 }] },
    { name: "琐事", minutes: 90, percentage: 32, behaviors: [{ actionName: "写 Controller 接口", minutes: 60 }] },
    { name: "休息", minutes: 40, percentage: 15, behaviors: [{ actionName: "散步放松", minutes: 25 }] },
    { name: "娱乐", minutes: 25, percentage: 8, behaviors: [{ actionName: "刷视频", minutes: 25 }] },
  ],
  weeklyTrend: [
    { date: "周一", progress: 10 },
    { date: "周二", progress: 25 },
    { date: "周三", progress: 45 },
    { date: "周四", progress: 40 },
    { date: "周五", progress: 65 },
    { date: "周六", progress: 80 },
    { date: "周日", progress: 95 },
  ],
};

export const mockFeedbackReport = {
  title: "本周成就总结：稳步前行的 Spring Boot 探索者",
  aiSummary: "你本周在核心接口、数据持久化和任务拆解方向都有稳定推进，很多小步已经串成了清晰的成长轨迹。",
  achievementTags: ["深度思考者", "代码稳健派"],
  suggestion: "下周可以把注意力集中在接口联调和文档整理上，不需要一次做完，保持节奏就很好。",
};

export const mockTasks = [
  { id: 101, title: "学会 Spring Boot 开发", category: "学习", totalProgress: 67.5, subtaskCount: 5, updatedAt: "2小时前" },
  { id: 102, title: "准备英语四级考试", category: "学习", totalProgress: 34, subtaskCount: 8, updatedAt: "昨天" },
  { id: 103, title: "完成数据库课设", category: "琐事", totalProgress: 42, subtaskCount: 6, updatedAt: "3小时前" },
  { id: 104, title: "每天坚持跑步 3 公里", category: "运动", totalProgress: 90, subtaskCount: 30, updatedAt: "1小时前" },
];

export const mockSubtasks = [
  { id: 1, content: "搭建环境与 Hello World", weight: 10, currentProgress: 100, isCompleted: 1 },
  { id: 2, content: "掌握 RestController 与注解", weight: 20, currentProgress: 100, isCompleted: 1 },
  { id: 3, content: "集成 MyBatis-Plus 数据持久化", weight: 25, currentProgress: 80, isCompleted: 0 },
  { id: 4, content: "业务逻辑 Service 层开发", weight: 25, currentProgress: 40, isCompleted: 0 },
  { id: 5, content: "Spring Security 安全认证", weight: 20, currentProgress: 0, isCompleted: 0 },
];

export const mockActionSyncResult = {
  updates: [
    { taskTitle: "学会 Spring Boot 开发", increment: 8.5, newTotalProgress: 76 },
    { taskTitle: "完成数据库课设", increment: 3, newTotalProgress: 45 },
  ],
  aiAnalysis: "干得漂亮！这次行为主要推进了 Spring Boot Web 开发部分，数据持久化模块也得到了巩固。",
};

export const mockHistory = {
  total: 2,
  list: [
    {
      id: 1,
      rawInput: "今天写了两个 Controller 接口并成功运行",
      contribution: 8.5,
      aiAnalysis: "推进了 Spring Boot Web 开发部分。",
      taskTitle: "学会 Spring Boot 开发",
      category: "学习",
      createdAt: "2026-06-08 21:20:00",
    },
    {
      id: 2,
      rawInput: "背了 50 个四级核心词汇",
      contribution: 2,
      aiAnalysis: "词汇积累有进展，适合继续保持。",
      taskTitle: "准备英语四级考试",
      category: "学习",
      createdAt: "2026-06-07 18:30:00",
    },
  ],
};

export const mockDeconstructResult = {
  taskId: 105,
  subTasks: [
    { content: "明确目标范围和验收标准", weight: 0.2 },
    { content: "拆分每天可完成的小任务", weight: 0.25 },
    { content: "完成核心代码或学习材料", weight: 0.35 },
    { content: "复盘总结并整理文档", weight: 0.2 },
  ],
  aiSuggestion: "先把目标拆成能在一天内完成的小块，会比一次性追求完整结果更稳。",
};

export function buildMockDeconstructResult(title, mode = "ai") {
  const isManual = mode === "manual";
  return {
    taskId: Date.now(),
    subTasks: isManual
      ? [
          { content: `明确「${title}」的完成标准`, weight: 0.25 },
          { content: "安排第一步可执行行动", weight: 0.25 },
          { content: "持续记录行为并同步进度", weight: 0.3 },
          { content: "复盘结果并整理成果", weight: 0.2 },
        ]
      : mockDeconstructResult.subTasks,
    aiSuggestion: isManual
      ? "已按当前后端能力保存任务，并生成基础子任务，后续可以通过行为记录继续推进进度。"
      : mockDeconstructResult.aiSuggestion,
  };
}
