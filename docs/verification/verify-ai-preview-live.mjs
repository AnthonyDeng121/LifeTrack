// 根目录执行：node docs/verification/verify-ai-preview-live.mjs，需要三端和真实模型。
// 只创建专用演示账号并预览，不保存计划，不输出认证信息。
import assert from 'node:assert/strict';
const base = process.env.LIFETRACK_API_BASE || 'http://localhost:8081/api/v1';
let token;
async function api(method, path, data) {
  const response = await fetch(base + path, {method, signal: AbortSignal.timeout(100000),
    headers: {'Content-Type':'application/json', ...(token ? {Authorization:`Bearer ${token}`} : {})},
    body: data === undefined ? undefined : JSON.stringify(data)});
  const result = await response.json();
  assert.equal(response.status, 200, `${path}: ${result.message || result.code}`);
  assert.equal(result.code, 200, `${path}: ${result.message || result.code}`);
  return result.data;
}
const options = await api('GET', '/auth/options');
assert.equal(options.demoPhoneEnabled, true);
token = (await api('POST', '/auth/login/phone', {phone:'199'+String(Date.now()).slice(-8),verifyCode:options.demoVerifyCode})).token;
const started = Date.now();
const today = new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const preview = await api('POST', '/tasks/deconstruct/preview', {title:'学习SQL SELECT基础查询',stepCount:2,
  startDate:today,endDate:today,targetDays:1,
  guidance:'第一步练习students表的id和name字段查询；最后综合查询并核对预期结果，通过验收。'});
assert.equal(preview.subTasks.length, 2);
assert.match(preview.subTasks.at(-1).content, /验收|通过|验证|测试|测验|达标/);
assert.equal((await api('GET', '/tasks')).length, 0);
console.log(JSON.stringify({passed:true,realModel:true,previewSteps:2,seconds:Number(((Date.now()-started)/1000).toFixed(1)),savedPlans:0}));
