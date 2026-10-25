import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { ref, computed, watch } from "vue";
import { validateCheckInForm, validatePlanPeriod } from "../src/services/planRules.js";

async function page(name, dependencies, exposed) {
  const source = await readFile(new URL(`../src/pages/${name}/${name}.vue`, import.meta.url), "utf8");
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*;$/gm, "");
  const bindings = { ref, computed, watch, onShow() {}, onLoad() {}, onMounted() {},
    uni: { showToast() {}, navigateTo() {} }, TASK_CATEGORIES: ["学习"],
    getBusinessDate: () => "2026-10-25", validateCheckInForm, validatePlanPeriod, ...dependencies };
  return new Function(...Object.keys(bindings), `${script}\nreturn { ${exposed} };`)(...Object.values(bindings));
}
const active = { id: 101, title: "学习", status: "IN_PROGRESS", startDate: "2026-10-01", endDate: "2026-10-31" };
let checkIns = [];
let duplicate = false;
let heldCheckIn;
let aiCalls = 0;
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
  getDashboardStats: async () => { throw new Error("Record page must not load dashboard"); },
  getCheckInHistory: async () => [{ id: 1 }],
  getActionHistory: async (page, size) => {
    historyCalls.push({ page, size });
    if (holdHistory) return holdHistory;
    if (historyFailure) throw new Error("history unavailable");
    return { list: historyRows.slice(page * size, (page + 1) * size), total: historyRows.length };
  },
  deleteAction: async id => { historyRows = historyRows.filter(row => row.id !== id); },
}, `plans, manualTaskId, manualContent, manualSaving, manualAlreadyChecked, manualMessage, submitDailyCheckIn, loadManualAttendance,
  history, historyPage, historyTotal, historyPageCount, historyError, historyLoading, loadHistory, loadData, nextHistoryPage, previousHistoryPage, retryHistory,
  syncResult, syncSummary, removeHistory`);
record.plans.value = [active];
await record.submitDailyCheckIn();
assert.equal(checkIns.length, 0);
record.manualTaskId.value = 101;
let releaseCheckIn;
heldCheckIn = new Promise(resolve => { releaseCheckIn = resolve; });
const saving = record.submitDailyCheckIn();
await record.submitDailyCheckIn();
assert.equal(checkIns.length, 1);
releaseCheckIn(); await saving; heldCheckIn = null;
assert.equal(aiCalls, 0);
assert.deepEqual(checkIns[0], { taskId: 101, content: "" });
assert.equal(record.manualAlreadyChecked.value, true);
await record.submitDailyCheckIn();
assert.equal(checkIns.length, 1);
record.manualAlreadyChecked.value = false;
duplicate = true;
await record.submitDailyCheckIn();
assert.equal(record.manualAlreadyChecked.value, true);
assert.equal(record.manualSaving.value, false);
record.manualAlreadyChecked.value = false;
await record.loadManualAttendance();
assert.equal(record.manualAlreadyChecked.value, true);
record.syncResult.value = { savedRecordCount: 2, newCheckInCount: 0, durationMinutes: 30, updates: [{ taskId: 101 }, { taskId: 101 }] };
assert.match(record.syncSummary.value, /30/);
await record.loadData();
assert.equal(record.history.value.length, 20);
assert.equal(record.historyTotal.value, 21);
assert.equal(record.historyPageCount.value, 2);
historyFailure = true;
await record.nextHistoryPage();
assert.equal(record.history.value.length, 20);
assert.notEqual(record.historyError.value, "");
historyFailure = false;
await record.retryHistory();
assert.deepEqual(historyCalls.at(-1), { page: 1, size: 20 });
assert.equal(record.history.value.length, 1);
assert.equal(record.historyPage.value, 1);
await record.previousHistoryPage();
assert.equal(record.history.value.length, 20);
assert.equal(record.historyPage.value, 0);
await record.nextHistoryPage();
await record.removeHistory(80);
assert.equal(record.history.value.length, 20);
assert.equal(record.historyTotal.value, 20);
assert.equal(record.historyPage.value, 0);
assert.equal(record.historyPageCount.value, 1);
let releaseOld;
holdHistory = new Promise(resolve => { releaseOld = resolve; });
const oldRequest = record.loadHistory(0);
holdHistory = null;
await record.loadHistory(0);
releaseOld({ list: [{ id: -1 }], total: 1 }); await oldRequest;
assert.equal(record.history.value.length, 20);
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

let savedMood;
const profile = await page("profile", {
  getUser: () => ({ username: "Test" }),
  getDashboardStats: async () => ({ currentAnxietyLevel: 6, weeklyTrend: [] }),
  getFeedbackReport: async () => null,
  updateMood: async value => { savedMood = value; },
}, "anxietyLevel, loadProfile, changeMood, saveMood");
await profile.loadProfile();
assert.equal(profile.anxietyLevel.value, 6);
profile.changeMood({ detail: { value: 4 } }); await profile.saveMood();
assert.equal(savedMood, 4);
const recordSource = await readFile(new URL("../src/pages/record/record.vue", import.meta.url), "utf8");
assert.doesNotMatch(recordSource, /anxietyLevel|loadMoreHistory|manualMinutes/);
console.log("One-click attendance, bounded history pages and profile mood checks passed.");
