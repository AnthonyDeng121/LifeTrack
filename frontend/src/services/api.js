import { endpoints } from "./config.js";
import { request, saveLogin } from "./http.js";

export function getAuthOptions() { return request({ ...endpoints.auth.options, showError: false }); }

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

export function getDashboardStats(showError = true) {
  return request({ ...endpoints.dashboard.stats, showError });
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
  return request({ ...endpoints.tasks.deconstruct(title, category, startDate, endDate, targetDays), timeout: 100000, showError: false });
}

export function createTaskManually(data) {
  return request({ ...endpoints.tasks.createManual(data) });
}

export function previewTaskByAI(data) {
  return request({ ...endpoints.tasks.preview(data), timeout: 100000, showError: false });
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

export function syncAction(rawInput, durationMinutes, taskId, requestId) {
  return request({ ...endpoints.actions.sync(rawInput, durationMinutes, taskId, requestId), timeout: 50000 });
}

export function addSubtask(taskId, data) { return request(endpoints.tasks.addSubtask(taskId, data)); }
export function saveDeferredLearningRecord(data) { return request(endpoints.actions.saveDeferred(data)); }
export function getDeferredLearningRecords(filters = {}) { return request(endpoints.actions.deferred(filters)); }
export function previewPeriodChange(taskId, startDate, endDate) { return request(endpoints.tasks.periodPreview(taskId, startDate, endDate)); }
export function retryDeferredLearningRecord(id) { return request({ ...endpoints.actions.retryDeferred(id), timeout: 50000 }); }
export function updateDeferredLearningRecord(id, data) { return request(endpoints.actions.updateDeferred(id, data)); }
export function deleteDeferredLearningRecord(id) { return request(endpoints.actions.deleteDeferred(id)); }
export function importDeferredLearningRecords() { return request(endpoints.actions.importDeferred); }

export function getActionHistory(page = 0, size = 10, taskId) {
  return request({ ...endpoints.actions.history(page, size, taskId) });
}

export function deleteAction(actionId) {
  return request({ ...endpoints.actions.delete(actionId) });
}

export function deleteTasks(taskIds) {
  return request({ ...endpoints.tasks.batchDelete(taskIds) });
}
