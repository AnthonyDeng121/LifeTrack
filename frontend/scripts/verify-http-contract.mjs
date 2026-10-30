// 由真实 Spring Boot HTTP 集成测试启动；仅 uni 的平台传输桥替换为 fetch，服务端响应不做 Mock。
import assert from 'node:assert/strict';
import { API_BASE_STORAGE_KEY, TOKEN_KEY } from '../src/services/config.js';
const base = process.env.TEST_API_BASE;
const token = process.env.TEST_API_TOKEN;
assert.match(base || '', /^http:\/\/localhost:\d+\/api\/v1$/);
assert.ok(token, 'Only run through the isolated backend integration test');
const storage = new Map([[API_BASE_STORAGE_KEY, base], [TOKEN_KEY, token]]);
const exchanges = [];
globalThis.uni = {
  getStorageSync: key => storage.get(key), setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: key => storage.delete(key), showToast() {}, reLaunch() {},
  request: async options => {
    try {
      const url = new URL(options.url);
      if (options.method === 'GET' && options.data) for (const [key,value] of Object.entries(options.data)) {
        if (value != null && value !== '') url.searchParams.set(key,value);
      }
      const response = await fetch(url, {method: options.method, headers: options.header,
        body: options.method === 'GET' || options.data === undefined ? undefined : JSON.stringify(options.data),
        signal: AbortSignal.timeout(options.timeout)});
      const data = await response.json();
      assert.equal(response.status,data.code,'HTTP status and business code must agree');
      exchanges.push({method: options.method,path: url.pathname,status: response.status});
      options.success({statusCode: response.status,data});
    } catch (e) { options.fail(e); }
  },
};
const api = await import('../src/services/api.js');
const today = new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
let id;
try {
  const result = await api.createTaskManually({title:'Frontend live contract',category:'学习',startDate:today,endDate:today,targetDays:1,
    subTasks:[{content:'验证前后端字段和HTTP错误语义',weight:1}]});
  id = result.taskId;
  assert.ok(Number.isInteger(id));
  assert.equal((await api.getTaskDetail(id)).status,'IN_PROGRESS');
  assert.equal((await api.getTasks({status:'IN_PROGRESS',startDate:today,endDate:today})).some(p=>p.id===id),true);
  assert.equal((await api.getSubtasks(id)).length,1);
  const stepBefore = (await api.getSubtasks(id))[0];
  await api.updateSubtask(stepBefore.id,{content:'Latest content',version:stepBefore.version});
  await assert.rejects(()=>api.updateSubtask(stepBefore.id,{content:'Stale overwrite',version:stepBefore.version}),e=>e.code===409);
  assert.equal((await api.getSubtasks(id))[0].content,'Latest content');
  await api.updateTask(id,{title:'Frontend live contract edited',version:(await api.getTaskDetail(id)).version});
  assert.equal((await api.getTaskDetail(id)).title,'Frontend live contract edited');
  await api.createCheckIn({taskId:id,content:''});
  await assert.rejects(()=>api.createCheckIn({taskId:id,content:''}),e=>e.code===409);
  assert.equal((await api.getCheckInHistory({taskId:id,startDate:today,endDate:today})).total,1);
  assert.equal((await api.getCheckInStats(id)).checkedInDays,1);
  assert.equal((await api.getCheckInStats(id)).completionRate,100);
  const dashboard = await api.getDashboardStats();
  assert.equal(dashboard.checkedInDays,1);
  assert.equal(dashboard.learningPlanCompletionRate,0);
  const history=await api.getActionHistory(0,20);
  assert.deepEqual(history.list,[]); assert.equal(history.total,0);
  const key = crypto.randomUUID();
  await api.saveDeferredLearningRecord({taskId:id,content:'frontend durable retry contract',durationMinutes:30,requestId:key});
  await api.saveDeferredLearningRecord({taskId:id,content:'frontend durable retry contract',durationMinutes:30,requestId:key});
  const pending=await api.getDeferredLearningRecords({taskId:id,page:0,size:20});
  assert.equal(pending.total,1); assert.equal(pending.list.length,1);
  assert.equal(pending.list[0].canRetry,true);
  const replacement=await api.updateDeferredLearningRecord(pending.list[0].id,{content:'corrected durable retry contract',durationMinutes:45});
  assert.notEqual(replacement.id,pending.list[0].id);
  assert.equal(replacement.recordedAt,pending.list[0].recordedAt);
  await assert.rejects(()=>api.retryDeferredLearningRecord(pending.list[0].id),e=>e.code===409);
  await assert.rejects(()=>api.saveDeferredLearningRecord({taskId:id,content:'frontend durable retry contract',durationMinutes:30,requestId:key}),e=>e.code===409);
  await api.deleteDeferredLearningRecord(replacement.id);
  assert.equal((await api.getDeferredLearningRecords({taskId:id})).total,0);
  assert.equal((await api.getActionHistory(0,20,id)).total,0);
  assert.deepEqual(await api.previewPeriodChange(id,today,today),[]);
} finally {
  if(id) { await api.deleteTask(id); await assert.rejects(()=>api.getTaskDetail(id),e=>e.code===404); }
}
assert.ok(exchanges.some(x=>x.status===409));
assert.ok(exchanges.some(x=>x.status===404));
console.log(JSON.stringify({passed:true,transport:'real HTTP',requests:exchanges.length,browser:false}));
