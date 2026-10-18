const clock = new Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", hour: "numeric", hourCycle: "h23" });
const calendar = new Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", month: "numeric", day: "numeric" });

export function getTimeGreeting(date = new Date()) {
  const hour = Number(clock.formatToParts(date).find(part => part.type === "hour").value);
  if (hour < 5) return "夜深了";
  if (hour < 11) return "早上好";
  if (hour < 14) return "中午好";
  if (hour < 18) return "下午好";
  return "晚上好";
}

export function getBusinessDateLabel(date = new Date()) {
  const parts = calendar.formatToParts(date);
  return `${Number(parts.find(part => part.type === "month").value)}月${Number(parts.find(part => part.type === "day").value)}日`;
}
