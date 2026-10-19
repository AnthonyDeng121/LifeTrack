// 手动运行；需三端和真实AI，使用隔离测试账号并清理临时计划。
import assert from "node:assert/strict";
const base = "http://localhost:8081/api/v1";
let token;
const ids = [];
const matchingOnly = process.argv.includes("--matching-only");
async function api(method, path, data) {
  const response = await fetch(base + path, { method, signal: AbortSignal.timeout(90000),
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: data === undefined ? undefined : JSON.stringify(data) });
  const result = await response.json(); assert.equal(result.code, 200, JSON.stringify(result)); return result.data;
}
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const end = new Date(Date.parse(today + "T12:00:00+08:00") + 6 * 86400000).toISOString().slice(0, 10);
token = (await api("POST", "/auth/login/wechat", { code: "matching-regression-" + Date.now() })).token;
try {
  if (!matchingOnly) {
    const draft = await api("POST", "/tasks/deconstruct/preview", { title: "系统学习计算机网络", stepCount: 10,
      guidance: "覆盖分层、链路、寻址、路由、TCP、UDP、DNS、HTTP与安全，最后通过综合抓包实验验收" });
    assert.equal(draft.subTasks.length, 10);
  }
  const steps = ["复习关系型数据库基本概念，包括表、行、列、主键、外键等", "练习SQL语句，包括SELECT、INSERT、UPDATE、DELETE基本操作",
    "学习数据库设计原则，包括范式理论与规范化", "复习索引、视图、存储过程与触发器",
    "练习事务管理，理解ACID原则和事务隔离级别", "学习数据库备份和恢复方法",
    "完成数据库设计项目，包括需求分析、ER图设计与SQL编写", "通过数据库综合模拟试题检验复习效果"];
  const plan = (await api("POST", "/tasks/manual", { title: "复习数据库", category: "学习", startDate: today, endDate: end,
    targetDays: 3, subTasks: steps.map(content => ({ content, weight: 0.125 })) })).taskId;
  ids.push(plan);
  const record = rawInput => api("POST", "/actions/sync", { taskId: plan, rawInput, durationInput: "30分钟" });
  const ambiguous = await record("整理了关系型数据库的笔记");
  assert.ok(ambiguous.updates.length <= 1, JSON.stringify(ambiguous));
  let persisted = await api("GET", `/tasks/${plan}/subtasks`);
  assert.ok(persisted.slice(1).every(step => step.currentProgress === 0), "笼统笔记不能推进SQL、事务等步骤");
  const specific = await record("整理了关系型数据库中表、行、列、主键、外键的概念笔记，并画出主外键关系示例");
  assert.equal(specific.updates.length, 1, JSON.stringify(specific));
  persisted = await api("GET", `/tasks/${plan}/subtasks`);
  assert.equal(specific.updates[0].subTaskId, persisted[0].id);
  assert.ok(persisted.slice(1).every(step => step.currentProgress === 0));
  const multiple = await record("练习SELECT查询与INSERT插入、UPDATE更新、DELETE删除的SQL语句，实际运行验证结果；另外练习事务管理的commit与rollback，验证ACID原则和事务隔离级别");
  assert.equal(multiple.updates.length, 2, JSON.stringify(multiple));
  assert.deepEqual(new Set(multiple.updates.map(step => step.subTaskId)), new Set([persisted[1].id, persisted[4].id]));
  assert.equal((await api("GET", `/check-ins/tasks/${plan}/stats`)).checkedInDays, 1);
  for (const step of persisted) await api("PUT", `/tasks/subtasks/${step.id}/complete`);
  const stats = await api("GET", "/dashboard/stats");
  assert.equal(stats.learningPlanCompletionRate, 100);
  assert.equal(stats.checkInCompletionRate, 33.3);
  const report = await api("GET", "/feedback/report");
  assert.ok(!report.title.includes("失败"), JSON.stringify(report));
  assert.match(report.planOutcomeSummary, /已完成 1 \/ 1 项/);
  assert.match(report.planOutcomeSummary, /累计打卡 1 次/);
  console.log(JSON.stringify({ result: "passed", tenStepPreviewTested: !matchingOnly, broadNoteUpdates: ambiguous.updates.length,
    specificNoteUpdates: 1, independentFactsUpdateTwoSteps: true, learningRate: stats.learningPlanCompletionRate,
    attendanceRate: stats.checkInCompletionRate, outcomeSummary: report.planOutcomeSummary, reportSummary: report.aiSummary }));
} finally { for (const id of ids) await api("DELETE", `/tasks/${id}`); }
