export const API_BASE_URL = "http://localhost:8081/api/v1";
export const ANDROID_EMULATOR_API_BASE_URL = "http://10.0.2.2:8081/api/v1";
export const API_BASE_STORAGE_KEY = "lifetrack_api_base_url";
export const TOKEN_KEY = "lifetrack_token";
export const USER_KEY = "lifetrack_user";
export const TASK_CATEGORIES = ["学习", "娱乐", "休息", "运动", "琐事"];

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
    devLogin: { method: "POST", path: "/auth/login/dev" },
    phoneLogin: (phone, verifyCode) => ({
      method: "POST",
      path: "/auth/login/phone",
      data: { phone, verifyCode },
    }),
    wechatLogin: (code) => ({
      method: "POST",
      path: "/auth/login/wechat",
      data: { code },
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
    list: { method: "GET", path: "/tasks" },
    subtasks: (taskId) => ({ method: "GET", path: `/tasks/${taskId}/subtasks` }),
    deconstruct: (title, category) => ({
      method: "POST",
      path: "/tasks/deconstruct",
      data: { title, category },
    }),
    createManual: (data) => ({
      method: "POST",
      path: "/tasks/manual",
      data,
    }),
    delete: (taskId) => ({ method: "DELETE", path: `/tasks/${taskId}` }),
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
  actions: {
    sync: (rawInput) => ({
      method: "POST",
      path: "/actions/sync",
      data: { rawInput },
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
