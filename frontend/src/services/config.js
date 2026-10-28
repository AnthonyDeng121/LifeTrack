export const API_BASE_URL = "http://localhost:8081/api/v1";
export const ANDROID_EMULATOR_API_BASE_URL = "http://10.0.2.2:8081/api/v1";
export const API_BASE_STORAGE_KEY = "lifetrack_api_base_url";
export const TOKEN_KEY = "lifetrack_token";
export const USER_KEY = "lifetrack_user";
export const TASK_CATEGORIES = ["学习", "娱乐", "休息", "运动", "琐事"];
export const PLAN_STATUSES = ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "EXPIRED", "ENDED"];

export function resolveApiBaseUrl({ storedBase = "", runtimeHost = "" } = {}) {
  if (storedBase) return storedBase;
  if (runtimeHost === "10.0.2.2") return ANDROID_EMULATOR_API_BASE_URL;
  if (runtimeHost && runtimeHost !== "localhost" && runtimeHost !== "127.0.0.1") {
    return `http://${runtimeHost}:8081/api/v1`;
  }
  return API_BASE_URL;
}

export const endpoints = {
  auth: {
    options: { method: "GET", path: "/auth/options" },
    devLogin: { method: "POST", path: "/auth/login/dev" },
    phoneLogin: (phone, verifyCode) => ({
      method: "POST",
      path: "/auth/login/phone",
      data: { phone, verifyCode },
    }),
  },
  dashboard: {
    stats: { method: "GET", path: "/dashboard/stats" },
    taskContributions: (taskId) => ({
      method: "GET",
      path: `/dashboard/tasks/${taskId}/contributions`,
    }),
  },
  tasks: {
    preview: (data) => ({ method: "POST", path: "/tasks/deconstruct/preview", data }),
    list: (filters = {}) => ({ method: "GET", path: "/tasks", data: filters }),
    detail: (taskId) => ({ method: "GET", path: `/tasks/${taskId}` }),
    update: (taskId, data) => ({ method: "PUT", path: `/tasks/${taskId}`, data }),
    subtasks: (taskId) => ({ method: "GET", path: `/tasks/${taskId}/subtasks` }),
    deconstruct: (title, category, startDate, endDate, targetDays) => ({
      method: "POST",
      path: "/tasks/deconstruct",
      data: { title, category, startDate, endDate, targetDays },
    }),
    createManual: (data) => ({
      method: "POST",
      path: "/tasks/manual",
      data,
    }),
    delete: (taskId) => ({ method: "DELETE", path: `/tasks/${taskId}` }),
    addSubtask: (taskId, data) => ({ method: "POST", path: `/tasks/${taskId}/subtasks`, data }),
    batchDelete: (taskIds) => ({ method: "POST", path: "/tasks/batch-delete", data: { taskIds } }),
    deleteSubtask: (subTaskId) => ({
      method: "DELETE",
      path: `/tasks/subtasks/${subTaskId}`,
    }),
    completeSubtask: (subTaskId) => ({
      method: "PUT",
      path: `/tasks/subtasks/${subTaskId}/complete`,
    }),
    updateSubtask: (subTaskId, data) => ({
      method: "PUT",
      path: `/tasks/subtasks/${subTaskId}`,
      data,
    }),
  },
  checkIns: {
    create: (data) => ({ method: "POST", path: "/check-ins", data }),
    history: (filters = {}) => ({ method: "GET", path: "/check-ins", data: filters }),
    stats: (taskId) => ({ method: "GET", path: `/check-ins/tasks/${taskId}/stats` }),
  },
  actions: {
    saveDeferred: (data) => ({ method: "POST", path: "/actions/deferred", data }),
    deferred: { method: "GET", path: "/actions/deferred" },
    retryDeferred: (id) => ({ method: "POST", path: `/actions/deferred/${id}/retry` }),
    importDeferred: { method: "POST", path: "/actions/deferred/import-legacy" },
    sync: (rawInput, durationMinutes, taskId) => ({
      method: "POST",
      path: "/actions/sync",
      data: { rawInput, durationMinutes, taskId },
    }),
    history: (page = 0, size = 10) => ({
      method: "GET",
      path: "/actions/history",
      data: { page, size },
    }),
    delete: (actionId) => ({ method: "DELETE", path: `/actions/${actionId}` }),
  },
  feedback: {
    report: { method: "GET", path: "/feedback/report" },
    mood: (anxietyLevel) => ({
      method: "POST",
      path: "/feedback/mood",
      data: { anxietyLevel },
    }),
  },
};
