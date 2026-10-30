import assert from 'node:assert/strict';
const base='http://localhost:8081/api/v1'; let token,id;
async function api(method,path,data){ const r=await fetch(base+path,{method,signal:AbortSignal.timeout(90000),headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:data===undefined?undefined:JSON.stringify(data)}); const j=await r.json();assert.equal(j.code,200,`${path}: ${JSON.stringify(j)}`);return j.data;}
const opts=await api('GET','/auth/options'); token=(await api('POST','/auth/login/phone',{phone:'199'+String(Date.now()).slice(-8),verifyCode:opts.demoVerifyCode})).token;
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
try {
 const draft=await api('POST','/tasks/deconstruct/preview',{title:'学习SQL SELECT基础查询',stepCount:2,startDate:today,endDate:new Date(Date.parse(today+'T12:00:00+08:00')+6*86400000).toISOString().slice(0,10),targetDays:3,guidance:'使用students表的id和name字段完成SELECT查询并验证返回结果，最后综合查询验收'});
 assert.equal(draft.subTasks.length,2);
 const saved=await api('POST','/tasks/manual',{title:'学习SQL SELECT基础查询',category:'学习',startDate:today,endDate:new Date(Date.parse(today+'T12:00:00+08:00')+6*86400000).toISOString().slice(0,10),targetDays:3,subTasks:draft.subTasks}); id=saved.taskId;
 const steps=await api('GET',`/tasks/${id}/subtasks`); assert.deepEqual(steps.map(s=>s.content),draft.subTasks.map(s=>s.content));
 const sync=await api('POST','/actions/sync',{taskId:id,rawInput:`今天仅练习第一步“${steps[0].content}”的部分内容：在数据库创建students表并插入id和name测试数据，执行SELECT id, name FROM students，确认结果包含预期学生姓名。仅记录第一步的练习投入；其余步骤及最后综合验收均未开展，没有完成全部目标。`,durationMinutes:30,requestId:crypto.randomUUID()});
 assert.ok(sync.updates.length>0,JSON.stringify(sync));
 const detail=await api('GET',`/tasks/${id}`);const stats=await api('GET','/dashboard/stats'); assert.ok(detail.totalProgress>0);assert.ok(Math.abs(stats.todayTotalProgress-detail.totalProgress)<0.11,JSON.stringify({gain:stats.todayTotalProgress,progress:detail.totalProgress}));
 const attendance=await api('GET',`/check-ins/tasks/${id}/stats`); assert.equal(attendance.checkedInDays,1);
 const history=await api('GET',`/actions/history?taskId=${id}`);assert.ok(history.list.length>0);
 const feedback=await api('GET','/feedback/report');assert.ok(!feedback.title.includes('失败'));assert.ok(feedback.aiSummary);
 console.log(JSON.stringify({passed:true,sameGeneratedPlan:true,persistedStepCount:steps.length,matchedStepCount:sync.updates.length,learningProgress:detail.totalProgress,weightedGain:stats.todayTotalProgress,attendanceRate:attendance.completionRate,historyCount:history.list.length,realFeedback:true}));
} finally {if(id)await api('DELETE',`/tasks/${id}`);}
