const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export function getInclusivePeriodDays(startDate, endDate) {
  if (!DATE_PATTERN.test(startDate || "") || !DATE_PATTERN.test(endDate || "")) return 0;
  if (Number(startDate.slice(0, 4)) < 1000 || Number(endDate.slice(0, 4)) < 1000) return 0;
  const start = Date.parse(`${startDate}T00:00:00Z`);
  const end = Date.parse(`${endDate}T00:00:00Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end) return 0;
  if (new Date(start).toISOString().slice(0, 10) !== startDate || new Date(end).toISOString().slice(0, 10) !== endDate) return 0;
  return Math.floor((end - start) / 86400000) + 1;
}

export function validatePlanPeriod(startDate, endDate, targetDays) {
  if (!DATE_PATTERN.test(startDate || "") || !DATE_PATTERN.test(endDate || "")
      || Number(startDate.slice(0, 4)) < 1000 || Number(endDate.slice(0, 4)) < 1000)
    return "请输入1000到9999年之间的有效日期";
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
  if ((content || "").trim().length > 500) return "打卡备注不能超过500字";
  const duration = Number(durationMinutes || 0);
  if (!Number.isInteger(duration) || duration < 0) return "学习时长必须为非负整数";
  return "";
}
