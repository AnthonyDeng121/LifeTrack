import { endpoints } from "./config";
import { request, saveLogin } from "./http";

export async function devLogin() {
  const data = await request({ ...endpoints.auth.devLogin });
  saveLogin(data);
  return data;
}

export async function phoneLogin(phone, verifyCode) {
  const data = await request({
    ...endpoints.auth.phoneLogin(phone, verifyCode),
  });
  saveLogin(data);
  return data;
}

export function getDashboardStats() {
  return request({ ...endpoints.dashboard.stats });
}

export function getFeedbackReport() {
  return request({ ...endpoints.feedback.report });
}

export function updateMood(anxietyLevel) {
  return request({ ...endpoints.feedback.mood(anxietyLevel) });
}

export function getTasks() {
  return request({ ...endpoints.tasks.list });
}

export function getSubtasks(taskId) {
  return request({ ...endpoints.tasks.subtasks(taskId) });
}

export function createTaskByAI(title, category) {
  return request({ ...endpoints.tasks.deconstruct(title, category) });
}

export function createTaskManually(data) {
  return request({ ...endpoints.tasks.createManual(data) });
}

export function completeSubtask(subTaskId) {
  return request({ ...endpoints.tasks.completeSubtask(subTaskId) });
}

export function deleteTask(taskId) {
  return request({ ...endpoints.tasks.delete(taskId) });
}

export function deleteSubtask(subTaskId) {
  return request({ ...endpoints.tasks.deleteSubtask(subTaskId) });
}

export function updateSubtask(subTaskId, data) {
  return request({ ...endpoints.tasks.updateSubtask(subTaskId, data) });
}

export function syncAction(rawInput, durationMinutes) {
  return request({ ...endpoints.actions.sync(rawInput, durationMinutes) });
}

export function getActionHistory(page = 0, size = 10) {
  return request({ ...endpoints.actions.history(page, size) });
}

export function deleteAction(actionId) {
  return request({ ...endpoints.actions.delete(actionId) });
}
