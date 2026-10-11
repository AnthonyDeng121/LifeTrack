const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export function getInclusivePeriodDays(startDate, endDate) {
  if (!DATE_PATTERN.test(startDate || "") || !DATE_PATTERN.test(endDate || "")) return 0;
  const start = Date.parse(`${startDate}T00:00:00Z`);
  const end = Date.parse(`${endDate}T00:00:00Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end) return 0;
  return Math.floor((end - start) / 86400000) + 1;
}

export function validatePlanPeriod(startDate, endDate, targetDays) {
  const periodDays = getInclusivePeriodDays(startDate, endDate);
  if (periodDays < 1) return "开始日期不能晚于结束日期";
  const target = Number(targetDays);
  if (!Number.isInteger(target) || target < 1 || target > periodDays) {
    return `目标天数应为1-${periodDays}天`;
  }
  return "";
}

export function validateCheckInForm({ content, durationMinutes, status }) {
  if (status !== "IN_PROGRESS") return "当前计划状态不可打卡";
  if (!(content || "").trim()) return "请填写打卡内容";
  const duration = Number(durationMinutes || 0);
  if (!Number.isInteger(duration) || duration < 0) return "学习时长必须为非负整数";
  return "";
}
