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

export function getFeedbackReport(showError = true) {
  return request({ ...endpoints.feedback.report, showError });
}

export function updateMood(anxietyLevel) {
  return request({ ...endpoints.feedback.mood(anxietyLevel) });
}

export function getTasks(filters = {}) {
  return request({ ...endpoints.tasks.list(filters) });
}

export function getTaskDetail(taskId) {
  return request({ ...endpoints.tasks.detail(taskId) });
}

export function updateTask(taskId, data) {
  return request({ ...endpoints.tasks.update(taskId, data) });
}

export function getSubtasks(taskId) {
  return request({ ...endpoints.tasks.subtasks(taskId) });
}

export function createTaskByAI(title, category, startDate, endDate, targetDays) {
  return request({ ...endpoints.tasks.deconstruct(title, category, startDate, endDate, targetDays), timeout: 50000, showError: false });
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

export function createCheckIn(data) {
  return request({ ...endpoints.checkIns.create(data) });
}

export function getCheckInHistory(filters = {}) {
  return request({ ...endpoints.checkIns.history(filters) });
}

export function getCheckInStats(taskId) {
  return request({ ...endpoints.checkIns.stats(taskId) });
}

export function syncAction(rawInput, durationInput, taskId) {
  return request({ ...endpoints.actions.sync(rawInput, durationInput, taskId) });
}

export function getActionHistory(page = 0, size = 10) {
  return request({ ...endpoints.actions.history(page, size) });
}

export function deleteAction(actionId) {
  return request({ ...endpoints.actions.delete(actionId) });
}

export function deleteTasks(taskIds) {
  return request({ ...endpoints.tasks.batchDelete(taskIds) });
}
