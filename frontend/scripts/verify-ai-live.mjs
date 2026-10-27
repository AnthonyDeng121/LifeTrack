// 手动执行：node frontend/scripts/verify-ai-live.mjs；需真实三端和AI密钥，产生模型调用。
import assert from "node:assert/strict";
const base = process.env.LIFETRACK_API_BASE || "http://localhost:8081/api/v1";
let token;
const ids = [];
async function api(method, path, data, expected = 200) {
  const response = await fetch(base + path, { method, signal: AbortSignal.timeout(90000),
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: data === undefined ? undefined : JSON.stringify(data) });
  const result = await response.json();
  assert.equal(result.code, expected, `${path}: ${JSON.stringify(result)}`);
  return result.data;
}
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const end = new Date(Date.parse(today + "T12:00:00+08:00") + 6 * 86400000).toISOString().slice(0, 10);
const loginOptions = await api("GET", "/auth/options");
assert.equal(loginOptions.demoPhoneEnabled, true, "Enable demo phone login in the explicit dev environment to run this script");
const testPhone = "199" + String(Date.now()).slice(-8);
token = (await api("POST", "/auth/login/phone", { phone: testPhone, verifyCode: loginOptions.demoVerifyCode })).token;
try {
  const draft = await api("POST", "/tasks/deconstruct/preview", { title: "学习计算机网络", stepCount: 3 });
  assert.equal(draft.subTasks.length, 3);
  assert.equal(draft.taskId, null);
  assert.equal((await api("GET", "/tasks")).length, 0, "预览不写库");
  const refined = await api("POST", "/tasks/deconstruct/preview", { title: "学习计算机网络", stepCount: 4,
    guidance: "已有分层基础，重点用Wireshark抓包分析DNS、HTTP和TCP，最后独立分析一次网页访问并验证协议顺序",
    previousSteps: draft.subTasks.map(s => s.content) });
  assert.equal(refined.subTasks.length, 4);
  assert.match(refined.subTasks.map(s => s.content).join(" "), /Wireshark|抓包/);
  assert.match(refined.subTasks.at(-1).content, /验收|验证|通过|测试|测验|达标/);
  assert.equal((await api("GET", "/tasks")).length, 0, "多次调整仍不写库");
  console.log(JSON.stringify({ previewCount: 3, refinedCount: 4, finalStep: refined.subTasks.at(-1).content }));
  const contents = ["用Maven创建Spring Boot应用，实现GET /hello返回hello并验证HTTP状态码200",
    "为GET /hello接口编写JUnit和MockMvc测试，断言状态码200及响应hello并运行测试全部通过"];
  async function create(targetDays) {
    const result = await api("POST", "/tasks/manual", { title: "完成Spring Boot Hello接口与自动化测试", category: "学习",
      startDate: today, endDate: end, targetDays, subTasks: contents.map(content => ({ content, weight: 0.5 })) });
    ids.push(result.taskId); return result.taskId;
  }
  const fullAttendance = await create(1);
  await api("POST", "/check-ins", { taskId: fullAttendance, content: "今天学习了Spring Boot", durationMinutes: 20 });
  assert.equal((await api("GET", `/check-ins/tasks/${fullAttendance}/stats`)).completionRate, 100);
  assert.equal((await api("GET", `/tasks/${fullAttendance}`)).totalProgress, 0);
  assert.equal((await api("GET", `/tasks/${fullAttendance}`)).status, "IN_PROGRESS");
  const plan = await create(3);
  const initial = (await api("GET", "/tasks")).find(t => t.id === plan).updatedAt;
  await new Promise(r => setTimeout(r, 1100));
  assert.equal((await api("GET", "/tasks")).find(t => t.id === plan).updatedAt, initial, "只读刷新不能改变更新时间");
  const record = text => api("POST", "/actions/sync", { taskId: plan, rawInput: text, durationMinutes: 30 });
  const irrelevant = await record("今天吃了香蕉，没有学习Spring Boot，也没有写代码");
  assert.equal(irrelevant.updates.length, 0);
  const partial = await record("今天刚开始用Maven创建Spring Boot项目，正在学习启动配置，还没完成hello接口，也没有运行测试");
  assert.ok(partial.updates.length, JSON.stringify(partial));
  assert.ok((await api("GET", `/tasks/${plan}`)).totalProgress < 100);
  const first = await record("今天已用Maven创建并启动Spring Boot应用，完成GET /hello接口返回hello，实际HTTP请求验证状态码200且响应hello，接口步骤的所有要求均已完成并验证通过。");
  assert.equal((await api("GET", `/tasks/${plan}/subtasks`))[0].currentProgress, 100, JSON.stringify(first));
  const second = await record("今天已为GET /hello接口编写完整JUnit和MockMvc自动化测试，断言HTTP状态码200及响应体hello，实际运行所有测试全部通过，测试步骤的全部要求已完成并验收通过。");
  const detail = await api("GET", `/tasks/${plan}`);
  assert.equal(detail.totalProgress, 100, JSON.stringify(second));
  assert.equal(detail.status, "COMPLETED");
  const stats = await api("GET", `/check-ins/tasks/${plan}/stats`);
  assert.equal(stats.checkedInDays, 1);
  assert.ok(stats.completionRate < 100, "提前学完仍保留真实打卡率");
  const history = await api("GET", "/actions/history?size=100");
  const lastLog = history.list.find(log => log.rawInput.includes("完整JUnit和MockMvc"));
  assert.ok(lastLog);
  await api("DELETE", `/actions/${lastLog.id}`, undefined, 400);
  assert.equal((await api("GET", `/tasks/${plan}`)).status, "COMPLETED");
  assert.equal((await api("GET", `/tasks/${plan}`)).totalProgress, 100);
  console.log(JSON.stringify({ result: "passed", naturalLanguageProgress: 100, attendanceDays: stats.checkedInDays,
    attendanceRate: stats.completionRate, attendanceDoesNotCompleteLearning: true, completedPlanCannotReopen: true, timestampsStableOnRead: true }));
} finally {
  for (const id of ids) await api("DELETE", `/tasks/${id}`);
}
