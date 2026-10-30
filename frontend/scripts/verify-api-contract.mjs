import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
const frontendReadme = await readFile(new URL("../README.md", import.meta.url), "utf8");

assert.equal(packageJson.scripts["dev:h5:lan"], "uni --host 0.0.0.0 --port 5173");
assert.equal(packageJson.scripts.verify, "npm run test:contracts && npm run test:flows && npm run build:h5");
assert.match(frontendReadme, /Windows/);
assert.match(frontendReadme, /npm run dev:h5:lan/);
assert.match(frontendReadme, /10\.0\.2\.2:5173/);

const {
    ANDROID_EMULATOR_API_BASE_URL,
    API_BASE_STORAGE_KEY,
    API_BASE_URL,
    TOKEN_KEY,
    TASK_CATEGORIES,
    endpoints,
    resolveApiBaseUrl,
  } = await import("../src/services/config.js");

assert.equal(API_BASE_URL, "http://localhost:8081/api/v1");
assert.equal(ANDROID_EMULATOR_API_BASE_URL, "http://10.0.2.2:8081/api/v1");
assert.equal(TOKEN_KEY, "lifetrack_token");
assert.equal(API_BASE_STORAGE_KEY, "lifetrack_api_base_url");
assert.deepEqual(TASK_CATEGORIES, ["学习", "娱乐", "休息", "运动", "琐事"]);
assert.equal(resolveApiBaseUrl({ storedBase: "http://192.168.1.20:8081/api/v1", runtimeHost: "10.0.2.2" }), "http://192.168.1.20:8081/api/v1");
assert.equal(resolveApiBaseUrl({ runtimeHost: "10.0.2.2" }), ANDROID_EMULATOR_API_BASE_URL);
assert.equal(resolveApiBaseUrl({ runtimeHost: "192.168.31.144" }), "http://192.168.31.144:8081/api/v1");
assert.equal(resolveApiBaseUrl({ runtimeHost: "localhost" }), API_BASE_URL);

assert.deepEqual(endpoints.auth.devLogin, { method: "POST", path: "/auth/login/dev" });
assert.deepEqual(endpoints.auth.options, { method: "GET", path: "/auth/options" });
assert.equal(endpoints.auth.wechatLogin, undefined);
assert.deepEqual(endpoints.auth.phoneLogin("18800001111", "123456"), {
  method: "POST",
  path: "/auth/login/phone",
  data: { phone: "18800001111", verifyCode: "123456" },
});
assert.deepEqual(endpoints.dashboard.stats, { method: "GET", path: "/dashboard/stats" });
assert.deepEqual(endpoints.tasks.list({ status: "IN_PROGRESS" }), {
  method: "GET", path: "/tasks", data: { status: "IN_PROGRESS" },
});
assert.deepEqual(endpoints.tasks.detail(101), { method: "GET", path: "/tasks/101" });
assert.deepEqual(endpoints.tasks.update(101, { title: "新标题" }), {
  method: "PUT", path: "/tasks/101", data: { title: "新标题" },
});
assert.deepEqual(endpoints.tasks.subtasks(101), { method: "GET", path: "/tasks/101/subtasks" });
assert.deepEqual(endpoints.tasks.deconstruct("学习 Spring Boot", "学习", "2026-10-11", "2026-10-31", 15), {
  method: "POST",
  path: "/tasks/deconstruct",
  data: { title: "学习 Spring Boot", category: "学习", startDate: "2026-10-11", endDate: "2026-10-31", targetDays: 15 },
});
assert.deepEqual(endpoints.tasks.completeSubtask(7), {
  method: "PUT",
  path: "/tasks/subtasks/7/complete",
});
assert.deepEqual(endpoints.checkIns.create({ taskId: 101, content: "学习一小时" }), {
  method: "POST", path: "/check-ins", data: { taskId: 101, content: "学习一小时" },
});
assert.deepEqual(endpoints.checkIns.history({ taskId: 101, startDate: "2026-10-01" }), {
  method: "GET", path: "/check-ins", data: { taskId: 101, startDate: "2026-10-01" },
});
assert.deepEqual(endpoints.checkIns.stats(101), {
  method: "GET", path: "/check-ins/tasks/101/stats",
});
assert.deepEqual(endpoints.actions.sync("今天写了两个 Controller 接口", 30, 101), {
  method: "POST",
  path: "/actions/sync",
  data: { rawInput: "今天写了两个 Controller 接口", durationMinutes: 30, taskId: 101 },
});
assert.deepEqual(endpoints.actions.history(), {
  method: "GET",
  path: "/actions/history",
  data: { page: 0, size: 10 },
});
assert.deepEqual(endpoints.feedback.mood(6), {
  method: "POST",
  path: "/feedback/mood",
  data: { anxietyLevel: 6 },
});

console.log("Frontend endpoint declaration checks passed (no backend requests).");

// 执行实际API封装，验证首发和补分析都给后端40秒AI读取预算留足传输余量。
const calls = [];
globalThis.uni = { getStorageSync() { return ""; }, request(options) {
  calls.push(options); queueMicrotask(() => options.success({ statusCode: 200, data: { code: 200, data: { updates: [] } } }));
} };
const liveApi = await import("../src/services/api.js");
await liveApi.syncAction("synthetic learning", 30, 101, "11111111-1111-1111-1111-111111111111");
await liveApi.retryDeferredLearningRecord(7);
assert.equal(calls[0].timeout, 50000); assert.equal(calls[1].timeout, calls[0].timeout);
await liveApi.updateDeferredLearningRecord(7, { content: "corrected", durationMinutes: 45 });
await liveApi.deleteDeferredLearningRecord(8);
assert.equal(calls[2].method, "PUT"); assert.equal(calls[2].url.endsWith("/actions/deferred/7"), true);
assert.deepEqual(calls[2].data, { content: "corrected", durationMinutes: 45 });
assert.equal(calls[3].method, "DELETE");
await liveApi.previewTaskByAI({ title: "synthetic plan", stepCount: 2, startDate: "2026-10-30", endDate: "2026-11-05", targetDays: 5 });
await liveApi.createTaskByAI("synthetic plan", "学习", "2026-10-30", "2026-11-05", 5);
assert.equal(calls[4].timeout, 100000); assert.equal(calls[5].timeout, 100000);
assert.ok(calls[4].timeout > 90000, "拆解前端预算需覆盖后端两次生成窗口");
assert.equal(calls[4].data.startDate, "2026-10-30"); assert.equal(calls[4].data.targetDays, 5);
