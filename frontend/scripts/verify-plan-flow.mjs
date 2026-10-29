import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { endpoints } from "../src/services/config.js";
import { getInclusivePeriodDays, validateCheckInForm, validatePlanPeriod } from "../src/services/planRules.js";
import { getTimeGreeting, getBusinessDateLabel, getBusinessDate } from "../src/services/businessTime.js";

for (const [hour, expected] of [[0, "夜深了"], [4, "夜深了"], [5, "早上好"], [10, "早上好"], [11, "中午好"], [13, "中午好"], [14, "下午好"], [17, "下午好"], [18, "晚上好"], [23, "晚上好"]]) {
  assert.equal(getTimeGreeting(new Date(`2026-10-18T${String(hour).padStart(2, "0")}:00:00+08:00`)), expected);
}
assert.equal(getBusinessDateLabel(new Date("2026-10-17T16:00:00Z")), "10月18日", "业务日期按上海跨日计算");
assert.equal(getBusinessDate(new Date("2026-10-17T16:00:00Z")), "2026-10-18");

assert.equal(getInclusivePeriodDays("2026-10-01", "2026-10-10"), 10);
assert.equal(validatePlanPeriod("2026-10-11", "2026-10-10", 1), "开始日期不能晚于结束日期");
assert.equal(validatePlanPeriod("2026-10-01", "2026-10-10", 11), "目标天数应为1-10天");
assert.equal(validatePlanPeriod("2026-10-01", "2026-10-10", 7), "");

assert.equal(validateCheckInForm({ content: "", durationMinutes: 0, status: "IN_PROGRESS" }), "");
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
assert.deepEqual(endpoints.actions.sync("学习编曲", 30, 101), {
  method: "POST", path: "/actions/sync", data: { rawInput: "学习编曲", durationMinutes: 30, taskId: 101 },
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
let previewCalls = [];
let savedPayload;
let previewWait;
const pageScript = tasksPage.match(/<script setup>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*;$/gm, "");
const page = new Function("computed", "ref", "watch", "onShow", "uni", "getTasks", "deleteTasks", "TASK_CATEGORIES", "previewTaskByAI", "createTaskManually", "validatePlanPeriod", `${pageScript}
  return { tasks, filteredTasks, batchMode, selectedIds, activeCategory, loadTasks, toggleBatchMode, toggleSelectAll, confirmBatchDelete, openTask,
    newTitle, newTargetDays, showCreator, aiResult, decompositionGuidance, stepCountIndex, generatePreview, createTask, openCreator, formatUpdatedAt, formatProgress };
`)(computed, ref, watch, () => {}, {
  showToast() {},
  showModal: async ({ success }) => success({ confirm: confirmDeletion }),
}, async () => storedTasks, async ids => {
  deletedIds = ids;
  storedTasks = storedTasks.filter(task => !ids.includes(task.id));
}, ["学习", "运动"], async payload => {
  previewCalls.push(payload);
  if (previewWait) await previewWait;
  return { subTasks: [{ content: "运行完整网络抓包实验并通过验收", weight: 1 }] };
}, async payload => { savedPayload = payload; return { taskId: 123 }; }, validatePlanPeriod);
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

page.openCreator();
page.newTitle.value = "学习计算机网络";
await nextTick();
await page.createTask();
assert.equal(page.showCreator.value, true, "预览不能关闭或保存任务");
assert.equal(savedPayload, undefined);
page.decompositionGuidance.value = "重点抓包，最后完成整体验收";
page.stepCountIndex.value = 2;
await page.createTask();
assert.equal(previewCalls.length, 2, "更改建议后必须先重新生成");
assert.equal(previewCalls[1].stepCount, 3);
assert.equal(previewCalls[1].previousSteps.length, 1);
assert.equal(savedPayload, undefined, "重新生成不能保存任务");
await page.createTask();
assert.equal(page.showCreator.value, false, "保存成功立即关闭新建框");
assert.equal(savedPayload.title, "学习计算机网络");
assert.equal(savedPayload.subTasks.length, 1);
assert.equal(page.formatUpdatedAt("2026-10-16T09:30:00"), "2026-10-16 09:30 更新");
assert.equal(page.formatUpdatedAt(null), "更新时间未知");
assert.equal(page.formatProgress(99.99), "99.9", "未实际完成不能因四舍五入显示100%");
assert.equal(page.formatProgress(100), "100");
page.openCreator();
page.newTitle.value = "学习HTTP";
page.decompositionGuidance.value = "先理解请求响应";
await nextTick();
let releasePreview;
previewWait = new Promise(resolve => { releasePreview = resolve; });
const pendingPreview = page.generatePreview();
page.decompositionGuidance.value = "增加curl验收";
releasePreview();
await pendingPreview;
previewWait = null;
const previousCallCount = previewCalls.length;
await page.createTask();
assert.equal(previewCalls.length, previousCallCount + 1, "请求期间改变建议也必须重新生成，不能保存旧要求的方案");
assert.equal(page.showCreator.value, true);
page.stepCountIndex.value = 9;
await page.generatePreview();
assert.equal(previewCalls.at(-1).stepCount, 10, "第十个步骤选项正确映射为10步");
assert.deepEqual(endpoints.tasks.preview({ title: "网络", guidance: "重点抓包" }), {
  method: "POST", path: "/tasks/deconstruct/preview", data: { title: "网络", guidance: "重点抓包" },
});

// 执行记录页提交逻辑：明确时长，先保存原文，失败记录可重试且不重复提交。
let syncResponse = { updates: [], aiAnalysis: "没有匹配" };
let syncArguments;
let manualPayload;
let deferredData = [];
let retriedId;
const recordScript = recordPage.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*;$/gm, "");
const recording = new Function("computed", "ref", "onShow", "uni", "syncAction", "getDashboardStats", "getActionHistory", "getTasks", "saveDeferredLearningRecord", "getDeferredLearningRecords", "retryDeferredLearningRecord", "getBusinessDate", `${recordScript.replace(/onHide\(.*;|onUnload\(.*;|onLoad\(.*;/g, "")}
  return { rawInput, durationHours, durationMinutes, readDurationMinutes, syncing, selectedTaskId, syncResult, resultIsRetry, pendingRecords, submitRecord, retryRecord, loadDeferredRecords };
`)(computed, ref, () => {}, { showToast() {} }, async (...args) => {
  syncArguments = args;
  return syncResponse;
}, async () => ({}), async () => ({ list: [] }), async () => [{ id:101, status:"IN_PROGRESS",startDate:"2000-01-01",endDate:"2099-12-31" }], async payload => { manualPayload = payload; }, async () => ({list:deferredData,total:deferredData.length}),
async id => { retriedId = id; deferredData = [{ id, status: "APPLIED" }]; return { updates: [{ taskId: 101, subTaskId: 1 }] }; }, getBusinessDate);
recording.rawInput.value = "学习编曲";
recording.durationMinutes.value = "30";
recording.selectedTaskId.value = 101;
await recording.submitRecord();
assert.deepEqual(syncArguments.slice(0, 3), ["学习编曲", 30, 101]);
assert.equal(recording.rawInput.value, "", "服务器已保存原文，重新分析使用记录ID");
assert.equal(recording.syncing.value, false);
syncResponse = { updates: [], matchingAmbiguous: true, aiAnalysis: "请补充具体知识点" };
recording.rawInput.value = "学习编曲"; recording.durationMinutes.value = "30";
await recording.submitRecord();
assert.equal(recording.rawInput.value, "");
syncResponse = { updates: [{ taskId: 101, subTaskId: 1, checkedInNow: true }] };
recording.rawInput.value = "学习编曲"; recording.durationMinutes.value = "30";
await recording.submitRecord();
assert.equal(recording.rawInput.value, "");
assert.equal(recording.durationMinutes.value, "");
syncResponse = { updates: [], analysisUnavailable: true, aiAnalysis: "AI 分析记录失败，请稍后重试" };
recording.rawInput.value = "学习编曲软件";
recording.durationHours.value = "1"; recording.durationMinutes.value = "30";
recording.selectedTaskId.value = 101;
await recording.submitRecord();
assert.equal(recording.syncResult.value.analysisUnavailable, true);
assert.deepEqual(syncArguments.slice(0, 3), ["学习编曲软件", 90, 101]);
assert.equal(recording.rawInput.value, "");
assert.equal(recording.syncing.value, false);
for (const [h, m, expected] of [["", "", null], ["0", "0", null], ["1.5", "0", null], ["1", "60", null], ["-1", "5", null], ["24", "1", null], ["24", "0", 1440], ["", "1", 1]]) {
  recording.durationHours.value = h; recording.durationMinutes.value = m;
  assert.equal(recording.readDurationMinutes(), expected);
}
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

// 实际执行详情页的新增与编辑分支，确认共用弹窗不会把新增误发为更新。
let addedStep;
let editedStep;
const detailScript = detailPage.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*;$/gm, "");
const detail = new Function("computed", "ref", "onLoad", "uni", "TASK_CATEGORIES", "getSubtasks", "getTaskDetail", "addSubtask", "updateSubtask", "watch", "getBusinessDate", `${detailScript}
  return { task, subtasks, checkInStats, targetFeedback, editingSubtask, showEditDialog, openAddDialog, openEditDialog, closeEditDialog, saveSubtask };
`)(computed, ref, () => {}, { showToast() {} }, ["学习"], async () => [], async () => ({ id: 101, status: "IN_PROGRESS" }),
async (id, data) => { addedStep = { id, data }; }, async (id, data) => { editedStep = { id, data }; }, watch, getBusinessDate);
detail.task.value.id = 101;
detail.task.value.status = "IN_PROGRESS";
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
detail.task.value.status = "COMPLETED";
detail.checkInStats.value.checkedInDays = 1;
assert.match(detail.targetFeedback.value, /停止新增/);
detail.task.value.status = "IN_PROGRESS";
detail.task.value.totalProgress = 100;
assert.match(detail.targetFeedback.value, /可继续打卡和填写学习情况/);
detail.task.value.status = "ENDED";
assert.match(detail.targetFeedback.value, /未计为学习完成/);
detail.checkInStats.value.checkedInDays = 20;
assert.match(detail.targetFeedback.value, /打卡目标已达成/);

// 执行真实的周期编辑流程：取消不写入、确认精确记录ID、请求失败和重复点击均不误保存。
let allowPrune = false;
let editPayloads = [];
let checkInHistory = [{ id: 21, checkInDate: "2026-10-23" }, { id: 22, checkInDate: "2026-10-25" }];
let historyFailure = false;
let saveFailure = false;
let modalCopy;
const editing = new Function("computed", "ref", "watch", "onLoad", "uni", "TASK_CATEGORIES", "getBusinessDate",
  "validatePlanPeriod", "getCheckInHistory", "getCheckInStats", "updateTask", "previewPeriodChange", `${detailScript}
  return { task, planForm, showPlanEdit, savingPlan, stepsEditable, planClosed, showEditDialog, editingSubtask,
    openPlanEdit, savePlan, openAddDialog, openEditDialog, saveSubtask, finishSubtask };
`)(computed, ref, watch, () => {}, {
  showToast() {},
  showModal: ({ content, success }) => { modalCopy = content; success({ confirm: allowPrune }); },
}, ["学习"], () => "2026-10-25", validatePlanPeriod,
async () => { if (historyFailure) throw new Error("history unavailable"); return checkInHistory; },
async () => ({}), async (id, payload) => {
  if (saveFailure) throw new Error("records changed, confirm again");
  editPayloads.push({ id, payload });
  return { ...editing.task.value, ...payload };
}, async (id,start,end) => { if (historyFailure) throw new Error("history unavailable"); return checkInHistory.filter(r=>r.checkInDate < start || r.checkInDate > end).map(r=>r.id); });
editing.task.value = { id: 101, version: 7, title: "学习", category: "学习", startDate: "2026-10-23", endDate: "2026-10-31", targetDays: 5, status: "IN_PROGRESS" };
editing.openPlanEdit();
editing.planForm.value.startDate = "2026-10-25";
await editing.savePlan();
assert.equal(editPayloads.length, 0, "取消清零不能调用修改接口");
assert.equal(editing.showPlanEdit.value, true);
assert.equal(editing.savingPlan.value, false);
assert.match(modalCopy, /未在新计划周期内的打卡记录将清零，是否确认修改/);
assert.match(modalCopy, /1 条/);
allowPrune = true;
await editing.savePlan();
assert.equal(editPayloads[0].payload.version, 7, "保存必须携带打开编辑表单时读取到的版本");
assert.equal(editPayloads.length, 1);
assert.equal(editPayloads[0].payload.discardOutsideCheckIns, true);
assert.deepEqual(editPayloads[0].payload.confirmedDiscardCheckInIds, [21]);
assert.equal(editing.showPlanEdit.value, false);
editing.openPlanEdit();
editing.planForm.value.startDate = "2026-10-26";
assert.equal(editing.planForm.value.status, "NOT_STARTED", "移动周期到未来时同步日期状态");
editing.planForm.value.startDate = "2026-10-25";
assert.equal(editing.planForm.value.status, "IN_PROGRESS");
editing.planForm.value.startDate = "2026-10-26";
historyFailure = true;
await editing.savePlan();
assert.equal(editPayloads.length, 1, "影响记录无法读取时禁止盲目提交");
historyFailure = false;
saveFailure = true;
await editing.savePlan();
assert.equal(editing.showPlanEdit.value, true, "后端拒绝过时确认后保留表单，等待重新确认");
saveFailure = false;
checkInHistory = [];
await editing.savePlan();
assert.equal(editPayloads.length, 2);
assert.equal(editPayloads[1].payload.discardOutsideCheckIns, undefined, "无删除影响不需要清零授权");
editing.task.value.status = "COMPLETED";
assert.equal(editing.planClosed.value, true);
assert.equal(editing.stepsEditable.value, false);
editing.openAddDialog();
editing.openEditDialog({ id: 9, content: "旧步骤", weight: 100 });
assert.equal(editing.showEditDialog.value, false, "完成计划禁止打开步骤修改框");
await editing.finishSubtask({ id: 9, currentProgress: 0 });
await editing.saveSubtask();
assert.equal(editPayloads.length, 2);
console.log("Frontend page logic checks passed (API/platform stubs; no browser).");
