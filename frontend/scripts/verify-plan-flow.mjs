import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { endpoints } from "../src/services/config.js";
import { getInclusivePeriodDays, validateCheckInForm, validatePlanPeriod } from "../src/services/planRules.js";
import { getTimeGreeting, getBusinessDateLabel } from "../src/services/businessTime.js";

for (const [hour, expected] of [[0, "夜深了"], [4, "夜深了"], [5, "早上好"], [10, "早上好"], [11, "中午好"], [13, "中午好"], [14, "下午好"], [17, "下午好"], [18, "晚上好"], [23, "晚上好"]]) {
  assert.equal(getTimeGreeting(new Date(`2026-10-18T${String(hour).padStart(2, "0")}:00:00+08:00`)), expected);
}
assert.equal(getBusinessDateLabel(new Date("2026-10-17T16:00:00Z")), "10月18日", "业务日期按上海跨日计算");

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
assert.deepEqual(endpoints.tasks.batchDelete([101, 102]), {
  method: "POST", path: "/tasks/batch-delete", data: { taskIds: [101, 102] },
});
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
assert.doesNotMatch(detailPage, /submitCheckIn|createCheckIn|v-model="checkInContent"/);
assert.doesNotMatch(dashboardPage, /goRecord|record-btn/);
assert.deepEqual(endpoints.actions.sync("学习编曲", "30分钟", 101), {
  method: "POST", path: "/actions/sync", data: { rawInput: "学习编曲", durationInput: "30分钟", taskId: 101 },
});
assert.match(profilePage, /基础计划与打卡功能仍可正常使用/);
for (const source of [dashboardPage, tasksPage, detailPage, recordPage, profilePage]) {
  const template = source.slice(0, source.indexOf('<script'));
  assert.doesNotMatch(template, /来自\s*\/|调用后端|后端联调|写入数据库|接口前缀/);
}

// 执行任务页实际逻辑，验证筛选后的批量选择、取消确认、删除与排序。
const { computed, ref, watch, nextTick } = await import("vue");
let storedTasks = ["EXPIRED", "COMPLETED", "IN_PROGRESS", "CANCELLED", "NOT_STARTED"]
  .map((status, index) => ({ id: index + 1, status, category: "学习" }));
let confirmDeletion = false;
let deletedIds = [];
const pageScript = tasksPage.match(/<script setup>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*;$/gm, "");
const page = new Function("computed", "ref", "watch", "onShow", "uni", "getTasks", "deleteTasks", "TASK_CATEGORIES", `${pageScript}
  return { tasks, filteredTasks, batchMode, selectedIds, activeCategory, loadTasks, toggleBatchMode, toggleSelectAll, confirmBatchDelete, openTask };
`)(computed, ref, watch, () => {}, {
  showToast() {},
  showModal: async ({ success }) => success({ confirm: confirmDeletion }),
}, async () => storedTasks, async ids => {
  deletedIds = ids;
  storedTasks = storedTasks.filter(task => !ids.includes(task.id));
}, ["学习", "运动"]);
await page.loadTasks();
assert.deepEqual(page.filteredTasks.value.map(task => task.status), ["IN_PROGRESS", "NOT_STARTED", "COMPLETED", "CANCELLED", "EXPIRED"]);
page.toggleBatchMode();
page.openTask(page.filteredTasks.value[0]);
assert.deepEqual(page.selectedIds.value, [3]);
page.confirmBatchDelete();
await new Promise(resolve => setTimeout(resolve, 0));
assert.deepEqual(deletedIds, [], "取消确认不能删除任务");
page.toggleSelectAll();
assert.equal(page.selectedIds.value.length, 5);
page.activeCategory.value = "运动";
await nextTick();
assert.deepEqual(page.selectedIds.value, [], "切换筛选必须清除选择");
page.activeCategory.value = "学习";
await nextTick();
page.openTask(page.filteredTasks.value[0]);
confirmDeletion = true;
page.confirmBatchDelete();
await new Promise(resolve => setTimeout(resolve, 0));
assert.deepEqual(deletedIds, [3]);
assert.equal(page.tasks.value.length, 4);
assert.deepEqual(page.selectedIds.value, []);

// 执行记录页提交逻辑：无匹配保留输入，成功才清空，时长和可选计划必须传到 API。
let syncResponse = { updates: [], aiAnalysis: "没有匹配" };
let syncArguments;
let manualPayload;
let deferredData = [];
let retriedId;
const recordScript = recordPage.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*;$/gm, "");
const recording = new Function("computed", "ref", "onShow", "uni", "syncAction", "getDashboardStats", "getActionHistory", "getTasks", "saveDeferredLearningRecord", "getDeferredLearningRecords", "retryDeferredLearningRecord", `${recordScript}
  return { rawInput, duration, syncing, selectedTaskId, syncResult, resultIsRetry, pendingRecords, submitRecord, submitManualCheckIn, retryRecord, loadDeferredRecords };
`)(computed, ref, () => {}, { showToast() {} }, async (...args) => {
  syncArguments = args;
  return syncResponse;
}, async () => ({}), async () => ({ list: [] }), async () => [], async payload => { manualPayload = payload; }, async () => deferredData,
async id => { retriedId = id; deferredData = [{ id, status: "APPLIED" }]; return { updates: [{ taskId: 101, subTaskId: 1 }] }; });
recording.rawInput.value = "学习编曲";
recording.duration.value = "30分钟";
recording.selectedTaskId.value = 101;
await recording.submitRecord();
assert.deepEqual(syncArguments, ["学习编曲", "30分钟", 101]);
assert.equal(recording.rawInput.value, "学习编曲", "无匹配保留输入，方便修改或选择计划后重试");
assert.equal(recording.syncing.value, false);
syncResponse = { updates: [{ taskId: 101, subTaskId: 1, checkedInNow: true }] };
await recording.submitRecord();
assert.equal(recording.rawInput.value, "");
assert.equal(recording.duration.value, "");
syncResponse = { updates: [], analysisUnavailable: true, aiAnalysis: "AI 分析记录失败，请稍后重试" };
recording.rawInput.value = "学习编曲软件";
recording.duration.value = "1.5小时";
recording.selectedTaskId.value = 101;
await recording.submitRecord();
assert.equal(recording.syncResult.value.analysisUnavailable, true);
assert.equal(recording.rawInput.value, "学习编曲软件");
await recording.submitManualCheckIn();
assert.deepEqual(manualPayload, { taskId: 101, content: "学习编曲软件", durationMinutes: 90 });
assert.equal(recording.rawInput.value, "");
assert.equal(recording.syncing.value, false);
deferredData = [{ id: 33, status: "PENDING" }];
await recording.loadDeferredRecords();
assert.equal(recording.pendingRecords.value.length, 1);
recording.rawInput.value = "还未提交的新草稿";
await recording.retryRecord(deferredData[0]);
assert.equal(retriedId, 33);
assert.equal(recording.pendingRecords.value.length, 0);
assert.equal(recording.resultIsRetry.value, true);
assert.equal(recording.rawInput.value, "还未提交的新草稿", "补分析不得清除当前输入的草稿");
assert.deepEqual(endpoints.tasks.addSubtask(101, { content: "练习", weight: 0.3 }), {
  method: "POST", path: "/tasks/101/subtasks", data: { content: "练习", weight: 0.3 },
});
assert.deepEqual(endpoints.actions.retryDeferred(33), { method: "POST", path: "/actions/deferred/33/retry" });
assert.deepEqual(endpoints.actions.saveDeferred(manualPayload), { method: "POST", path: "/actions/deferred", data: manualPayload });

// 实际执行详情页的新增与编辑分支，确认共用弹窗不会把新增误发为更新。
let addedStep;
let editedStep;
const detailScript = detailPage.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*;$/gm, "");
const detail = new Function("computed", "ref", "onLoad", "uni", "TASK_CATEGORIES", "getSubtasks", "getTaskDetail", "addSubtask", "updateSubtask", `${detailScript}
  return { task, subtasks, checkInStats, targetFeedback, editingSubtask, showEditDialog, openAddDialog, openEditDialog, closeEditDialog, saveSubtask };
`)(computed, ref, () => {}, { showToast() {} }, ["学习"], async () => [], async () => ({ id: 101 }),
async (id, data) => { addedStep = { id, data }; }, async (id, data) => { editedStep = { id, data }; });
detail.task.value.id = 101;
detail.openAddDialog();
detail.editingSubtask.value.content = "新增学习步骤";
detail.editingSubtask.value.weight = 25;
await detail.saveSubtask();
assert.deepEqual(addedStep, { id: 101, data: { content: "新增学习步骤", weight: 0.25 } });
assert.equal(detail.showEditDialog.value, false);
detail.openEditDialog({ id: 9, content: "已有步骤", weight: 30 });
await detail.saveSubtask();
assert.deepEqual(editedStep, { id: 9, data: { content: "已有步骤", weight: 0.3 } });
detail.openEditDialog({ id: 9, content: "低权重步骤", weight: 1 });
assert.equal(detail.editingSubtask.value.weight, 1, "接口返回的1%不能被误显示为100%");
detail.checkInStats.value = { targetDays: 20, checkedInDays: 5 };
assert.match(detail.targetFeedback.value, /15 个打卡日/);
detail.task.value.status = "EXPIRED";
assert.match(detail.targetFeedback.value, /本期已结束/);
detail.checkInStats.value.checkedInDays = 20;
assert.match(detail.targetFeedback.value, /已达成打卡目标/);
console.log("Plan and check-in flow checks passed.");
