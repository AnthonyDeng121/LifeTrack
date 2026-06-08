import { endpoints } from "./config";
import { request, saveLogin } from "./http";
import {
  mockActionSyncResult,
  buildMockDeconstructResult,
  mockDashboardStats,
  mockFeedbackReport,
  mockHistory,
  mockSubtasks,
  mockTasks,
  mockUser,
} from "./mockData";

export async function devLogin() {
  const data = await request({ ...endpoints.auth.devLogin, mock: mockUser });
  saveLogin(data);
  return data;
}

export async function phoneLogin(phone, verifyCode) {
  const data = await request({
    ...endpoints.auth.phoneLogin(phone, verifyCode),
    mock: { ...mockUser, username: phone ? `用户${phone.slice(-4)}` : mockUser.username },
  });
  saveLogin(data);
  return data;
}

export function getDashboardStats() {
  return request({ ...endpoints.dashboard.stats, mock: mockDashboardStats });
}

export function getFeedbackReport() {
  return request({ ...endpoints.feedback.report, mock: mockFeedbackReport });
}

export function updateMood(anxietyLevel) {
  return request({ ...endpoints.feedback.mood(anxietyLevel), mock: null });
}

export function getTasks() {
  return request({ ...endpoints.tasks.list, mock: mockTasks });
}

export function getSubtasks(taskId) {
  return request({ ...endpoints.tasks.subtasks(taskId), mock: mockSubtasks });
}

export function createTaskByAI(title, category) {
  return request({ ...endpoints.tasks.deconstruct(title, category), mock: buildMockDeconstructResult(title, "ai") });
}

export function createTaskManually(title, category) {
  return request({ ...endpoints.tasks.deconstruct(title, category), mock: buildMockDeconstructResult(title, "manual") });
}

export function completeSubtask(subTaskId) {
  return request({ ...endpoints.tasks.completeSubtask(subTaskId), mock: null });
}

export function deleteTask(taskId) {
  return request({ ...endpoints.tasks.delete(taskId), mock: null });
}

export function syncAction(rawInput) {
  return request({ ...endpoints.actions.sync(rawInput), mock: mockActionSyncResult });
}

export function getActionHistory(page = 0, size = 10) {
  return request({ ...endpoints.actions.history(page, size), mock: mockHistory });
}

export function deleteAction(actionId) {
  return request({ ...endpoints.actions.delete(actionId), mock: null });
}
