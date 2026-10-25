import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { ref, computed, watch } from "vue";
import { validateCheckInForm, validatePlanPeriod } from "../src/services/planRules.js";

async function page(name, dependencies, exposed) {
  const source = await readFile(new URL(`../src/pages/${name}/${name}.vue`, import.meta.url), "utf8");
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*;$/gm, "");
  const bindings = { ref, computed, watch, onShow() {}, onLoad() {},
    uni: { showToast() {}, navigateTo() {} }, TASK_CATEGORIES: ["学习"],
    getBusinessDate: () => "2026-10-25", validateCheckInForm, validatePlanPeriod, ...dependencies };
  return new Function(...Object.keys(bindings), `${script}\nreturn { ${exposed} };`)(...Object.values(bindings));
}
const active = { id: 101, title: "学习", status: "IN_PROGRESS", startDate: "2026-10-01", endDate: "2026-10-31" };
let checkIns = [];
let duplicate = false;
let heldCheckIn;
let aiCalls = 0;
let moodFailure = false;
let holdMood = false;
let historyFailure = false;
let holdHistory;
const historyCalls = [];
let historyRows = Array.from({ length: 21 }, (_, index) => ({ id: 100 - index, rawInput: `学习记录${index}` }));
const record = await page("record", {
  createCheckIn: async payload => {
    checkIns.push(payload);
    if (heldCheckIn) await heldCheckIn;
    if (duplicate) throw Object.assign(new Error("重复打卡"), { code: 409 });
    return { ...payload, id: 1 };
  },
  syncAction: async () => { aiCalls++; return { updates: [] }; },
  getTasks: async () => [active], getDeferredLearningRecords: async () => [],
  getDashboardStats: async showError => {
    assert.equal(showError, false, "独立情绪加载不弹出全局错误");
    if (holdMood) return new Promise(() => {});
    if (moodFailure) throw new Error("dashboard unavailable");
    return { currentAnxietyLevel: 3 };
  },
  getActionHistory: async (page, size) => {
    historyCalls.push({ page, size });
    if (holdHistory) return holdHistory;
    if (historyFailure) throw new Error("history unavailable");
    return { list: historyRows.slice(page * size, (page + 1) * size), total: historyRows.length };
  },
  deleteAction: async id => { historyRows = historyRows.filter(row => row.id !== id); },
}, `plans, manualTaskId, manualDate, manualContent, manualMinutes, manualSaving, manualMessage, submitDailyCheckIn,
  history, historyTotal, historyHasMore, historyError, historyLoading, loadHistory, loadData, loadMoreHistory, retryHistory,
  loadMood, moodError, syncResult, syncSummary, removeHistory`);
record.plans.value = [active];
record.manualContent.value = "完成数据库练习";
record.manualMinutes.value = "30";
await record.submitDailyCheckIn();
assert.equal(checkIns.length, 0, "未选计划不得提交");
record.manualTaskId.value = 101;
record.manualDate.value = "2026-11-01";
await record.submitDailyCheckIn();
assert.equal(checkIns.length, 0, "未来或越界日期不得提交");
record.manualDate.value = "2026-10-25";
let releaseCheckIn;
heldCheckIn = new Promise(resolve => { releaseCheckIn = resolve; });
const saving = record.submitDailyCheckIn();
await record.submitDailyCheckIn();
assert.equal(checkIns.length, 1, "请求中再次点击只写入一次");
releaseCheckIn(); await saving; heldCheckIn = null;
assert.equal(aiCalls, 0, "普通打卡完全不调用 AI");
assert.deepEqual(checkIns[0], { taskId: 101, checkInDate: "2026-10-25", content: "完成数据库练习", durationMinutes: 30 });
assert.match(record.manualMessage.value, /新增学习记录 0 条/);
duplicate = true;
await record.submitDailyCheckIn();
assert.match(record.manualMessage.value, /当天已打卡，未新增记录或重复累计时长/);
assert.equal(record.manualContent.value, "完成数据库练习", "重复或失败保留原输入");
assert.equal(record.manualSaving.value, false);
record.syncResult.value = { savedRecordCount: 2, newCheckInCount: 0, durationMinutes: 30, updates: [{ taskId: 101 }, { taskId: 101 }] };
assert.match(record.syncSummary.value, /新增 2 条步骤学习记录，新增 0 条正式打卡，记录时长合计 30 分钟/);

holdMood = true;
void record.loadMood();
await record.loadData();
assert.equal(record.history.value.length, 20, "看板一直未返回也不阻塞历史");
assert.equal(record.historyTotal.value, 21);
assert.equal(record.historyHasMore.value, true);
historyFailure = true;
await record.loadMoreHistory();
assert.equal(record.history.value.length, 20, "翻页失败保留已加载记录");
assert.match(record.historyError.value, /历史记录加载失败/);
historyFailure = false;
await record.retryHistory();
assert.deepEqual(historyCalls.at(-1), { page: 1, size: 20 }, "翻页失败重试原页");
assert.equal(record.history.value.length, 21);
assert.equal(new Set(record.history.value.map(row => row.id)).size, 21);
assert.equal(record.historyHasMore.value, false);
holdMood = false; moodFailure = true;
await record.loadMood();
assert.equal(record.history.value.length, 21);
assert.equal(record.historyError.value, "");
assert.match(record.moodError.value, /情绪状态/);
await record.removeHistory(100);
assert.equal(record.history.value.length, 20);
assert.equal(record.historyTotal.value, 20);
assert.equal(record.historyHasMore.value, false);
assert.equal(record.history.value.some(row => row.id === 100), false, "删除后重取首页，不跳过补位记录");

let releaseOld;
holdHistory = new Promise(resolve => { releaseOld = resolve; });
const oldRequest = record.loadHistory(true);
holdHistory = null;
await record.loadHistory(true);
releaseOld({ list: [{ id: -1 }], total: 1 }); await oldRequest;
assert.equal(record.history.value.length, 20, "较旧刷新结果不能覆盖较新结果");
assert.equal(record.history.value.some(row => row.id === -1), false);

const taskFilters = [];
const tasks = await page("tasks", { getTasks: async filters => { taskFilters.push(filters); return [active]; } },
  "filterStartDate, filterEndDate, dateFilterError, activeStatus, selectedIds, applyDateFilters, resetDateFilters");
tasks.filterStartDate.value = "2026-10-25"; tasks.filterEndDate.value = "2026-10-01";
await tasks.applyDateFilters();
assert.equal(taskFilters.length, 0);
assert.match(tasks.dateFilterError.value, /不能晚于/);
tasks.filterEndDate.value = "2026-10-31"; tasks.activeStatus.value = "IN_PROGRESS"; tasks.selectedIds.value = [101];
await tasks.applyDateFilters();
assert.deepEqual(taskFilters[0], { startDate: "2026-10-25", endDate: "2026-10-31", status: "IN_PROGRESS" });
assert.deepEqual(tasks.selectedIds.value, [], "范围变化后清除批量选择");
await tasks.resetDateFilters();
assert.deepEqual(taskFilters.at(-1), { status: "IN_PROGRESS" });

const attendanceFilters = [];
let statsFailure = false;
let attendanceFailure = false;
const detail = await page("task-detail", {
  getCheckInHistory: async filters => {
    attendanceFilters.push(filters);
    if (attendanceFailure) throw new Error("attendance unavailable");
    return [{ id: 11, checkInDate: "2026-10-25" }];
  },
  getCheckInStats: async () => { if (statsFailure) throw new Error("stats unavailable"); return { checkedInDays: 5 }; },
}, "task, historyStartDate, historyEndDate, historyFilterError, historyLoadError, checkInHistory, checkInStats, checkInError, filterCheckInHistory, resetCheckInHistory, loadCheckIns");
detail.task.value.id = 101;
detail.historyStartDate.value = "2026-10-26"; detail.historyEndDate.value = "2026-10-25";
await detail.filterCheckInHistory();
assert.equal(attendanceFilters.length, 0);
detail.historyStartDate.value = "2026-10-25";
await detail.filterCheckInHistory();
assert.deepEqual(attendanceFilters[0], { taskId: 101, startDate: "2026-10-25", endDate: "2026-10-25" });
assert.equal(detail.checkInStats.value.checkedInDays, 0, "筛选历史不修改概览统计");
statsFailure = true;
await detail.loadCheckIns();
assert.equal(detail.checkInHistory.value.length, 1, "统计接口失败时仍可查询打卡历史");
assert.match(detail.checkInError.value, /打卡数据加载失败/);
statsFailure = false; attendanceFailure = true;
await detail.loadCheckIns();
assert.equal(detail.checkInStats.value.checkedInDays, 5, "历史接口失败不隐藏可用概览");
assert.match(detail.historyLoadError.value, /已保留上次结果/);
attendanceFailure = false;
await detail.resetCheckInHistory();
assert.deepEqual(attendanceFilters.at(-1), { taskId: 101 });
console.log("Manual check-in, date filters, history pagination and failure isolation checks passed.");
