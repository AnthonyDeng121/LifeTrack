import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { endpoints } from "../src/services/config.js";
import { getInclusivePeriodDays, validateCheckInForm, validatePlanPeriod } from "../src/services/planRules.js";

assert.equal(getInclusivePeriodDays("2026-10-01", "2026-10-10"), 10);
assert.equal(validatePlanPeriod("2026-10-11", "2026-10-10", 1), "开始日期不能晚于结束日期");
assert.equal(validatePlanPeriod("2026-10-01", "2026-10-10", 11), "目标天数应为1-10天");
assert.equal(validatePlanPeriod("2026-10-01", "2026-10-10", 7), "");

assert.equal(validateCheckInForm({ content: "", durationMinutes: 30, status: "IN_PROGRESS" }), "请填写打卡内容");
assert.equal(validateCheckInForm({ content: "学习", durationMinutes: -1, status: "IN_PROGRESS" }), "学习时长必须为非负整数");
assert.equal(validateCheckInForm({ content: "学习", durationMinutes: 30, status: "CANCELLED" }), "当前计划状态不可打卡");
assert.equal(validateCheckInForm({ content: "学习", durationMinutes: 30, status: "IN_PROGRESS" }), "");

const planPayload = {
  title: "Spring Boot 学习计划",
  category: "学习",
  startDate: "2026-10-01",
  endDate: "2026-10-10",
  targetDays: 7,
  subTasks: [{ content: "完成接口练习", weight: 1 }],
};
assert.deepEqual(endpoints.tasks.createManual(planPayload), { method: "POST", path: "/tasks/manual", data: planPayload });
assert.deepEqual(endpoints.tasks.update(101, { status: "COMPLETED" }), {
  method: "PUT", path: "/tasks/101", data: { status: "COMPLETED" },
});
assert.deepEqual(endpoints.checkIns.create({ taskId: 101, checkInDate: "2026-10-10", content: "学习", durationMinutes: 30 }), {
  method: "POST", path: "/check-ins", data: { taskId: 101, checkInDate: "2026-10-10", content: "学习", durationMinutes: 30 },
});
assert.deepEqual(endpoints.checkIns.history({ taskId: 101 }), { method: "GET", path: "/check-ins", data: { taskId: 101 } });
assert.deepEqual(endpoints.checkIns.stats(101), { method: "GET", path: "/check-ins/tasks/101/stats" });

const [dashboardPage, tasksPage, detailPage, recordPage, profilePage] = await Promise.all([
  readFile(new URL("../src/pages/dashboard/dashboard.vue", import.meta.url), "utf8"),
  readFile(new URL("../src/pages/tasks/tasks.vue", import.meta.url), "utf8"),
  readFile(new URL("../src/pages/task-detail/task-detail.vue", import.meta.url), "utf8"),
  readFile(new URL("../src/pages/record/record.vue", import.meta.url), "utf8"),
  readFile(new URL("../src/pages/profile/profile.vue", import.meta.url), "utf8"),
]);
assert.match(dashboardPage, /Promise\.allSettled/);
assert.match(dashboardPage, /AI 服务暂不可用/);
assert.match(tasksPage, /已切换为手动创建/);
assert.match(detailPage, /打卡数据加载失败/);
assert.match(recordPage, /暂无行为记录/);
assert.match(profilePage, /基础计划与打卡功能仍可正常使用/);

console.log("Plan and check-in flow checks passed.");
