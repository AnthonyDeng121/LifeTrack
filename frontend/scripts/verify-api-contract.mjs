import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
const frontendReadme = await readFile(new URL("../README.md", import.meta.url), "utf8");

assert.equal(packageJson.scripts["dev:h5:lan"], "uni --host 0.0.0.0 --port 5173");
assert.equal(packageJson.scripts.verify, "npm run test:contracts && npm run build:h5");
assert.match(frontendReadme, /Windows/);
assert.match(frontendReadme, /npm run dev:h5:lan/);
assert.match(frontendReadme, /10\.0\.2\.2:5173/);

const [
  {
    ANDROID_EMULATOR_API_BASE_URL,
    API_BASE_STORAGE_KEY,
    API_BASE_URL,
    TOKEN_KEY,
    TASK_CATEGORIES,
    endpoints,
    resolveApiBaseUrl,
  },
  { mockTasks, mockDashboardStats },
] = await Promise.all([
  import("../src/services/config.js"),
  import("../src/services/mockData.js"),
]);

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
assert.deepEqual(endpoints.auth.phoneLogin("18800001111", "123456"), {
  method: "POST",
  path: "/auth/login/phone",
  data: { phone: "18800001111", verifyCode: "123456" },
});
assert.deepEqual(endpoints.dashboard.stats, { method: "GET", path: "/dashboard/stats" });
assert.deepEqual(endpoints.tasks.list, { method: "GET", path: "/tasks" });
assert.deepEqual(endpoints.tasks.subtasks(101), { method: "GET", path: "/tasks/101/subtasks" });
assert.deepEqual(endpoints.tasks.deconstruct("学习 Spring Boot", "学习"), {
  method: "POST",
  path: "/tasks/deconstruct",
  data: { title: "学习 Spring Boot", category: "学习" },
});
assert.deepEqual(endpoints.tasks.completeSubtask(7), {
  method: "PUT",
  path: "/tasks/subtasks/7/complete",
});
assert.deepEqual(endpoints.actions.sync("今天写了两个 Controller 接口"), {
  method: "POST",
  path: "/actions/sync",
  data: { rawInput: "今天写了两个 Controller 接口" },
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

assert.ok(mockTasks.every((task) => "id" in task && "title" in task && "totalProgress" in task && "category" in task));
assert.ok(mockTasks.every((task) => TASK_CATEGORIES.includes(task.category)));
assert.ok(Array.isArray(mockDashboardStats.weeklyTrend));
assert.ok(Array.isArray(mockDashboardStats.timeDistribution));

console.log("API contract checks passed.");
