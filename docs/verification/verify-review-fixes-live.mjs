// 根目录执行 node docs/verification/verify-review-fixes-live.mjs；需本机dev三端及真实模型。
// 仅使用合成记录，自动清理临时计划；不输出认证信息。
import assert from 'node:assert/strict';
const base='http://localhost:8081/api/v1';let token,id;
async function api(method,path,data,expected=200){
 const r=await fetch(base+path,{method,signal:AbortSignal.timeout(90000),headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:data===undefined?undefined:JSON.stringify(data)});
 const j=await r.json();assert.equal(r.status,expected,path);assert.equal(j.code,expected,JSON.stringify(j));return j.data;
}
const opts=await api('GET','/auth/options');assert.equal(opts.demoPhoneEnabled,true);
token=(await api('POST','/auth/login/phone',{phone:'199'+String(Date.now()).slice(-8),verifyCode:opts.demoVerifyCode})).token;
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const facts='今天在students测试表执行SELECT id, name FROM students，核对返回的id与name字段和预期数据一致，仅练习基础查询。';
try{
 id=(await api('POST','/tasks/manual',{title:'SQL基础查询验收',category:'学习',startDate:today,endDate:new Date(Date.parse(today+'T12:00:00+08:00')+6*86400000).toISOString().slice(0,10),targetDays:3,subTasks:[{content:'练习SELECT基本查询并验证students表中的id和name字段',weight:1}]})).taskId;
 const first=await api('POST','/actions/sync',{taskId:id,rawInput:facts,durationMinutes:30,requestId:crypto.randomUUID()});assert.equal(first.savedRecordCount,1,JSON.stringify(first));
 const step=(await api('GET',`/tasks/${id}/subtasks`))[0];
 const oldLog=(await api('GET',`/actions/history?taskId=${id}`)).list[0];
 await api('PUT',`/tasks/subtasks/${step.id}/complete`);
 await api('DELETE',`/actions/${oldLog.id}`);
 let detail=await api('GET',`/tasks/${id}`);assert.equal(detail.totalProgress,100);assert.equal(detail.learningDurationMinutes,0);
 assert.equal((await api('GET',`/check-ins/tasks/${id}/stats`)).checkedInDays,0);
 const key=crypto.randomUUID();
 const old=await api('POST','/actions/deferred',{taskId:id,content:'待修正描述',durationMinutes:20,requestId:key});
 const changed=await api('PUT',`/actions/deferred/${old.id}`,{content:facts,durationMinutes:45});assert.equal(changed.recordedAt,old.recordedAt);
 await api('POST','/actions/deferred',{taskId:id,content:'待修正描述',durationMinutes:20,requestId:key},409);
 const retry=await api('POST',`/actions/deferred/${changed.id}/retry`);assert.equal(retry.durationMinutes,45,JSON.stringify(retry));
 await api('POST',`/actions/deferred/${changed.id}/retry`,undefined,409);
 detail=await api('GET',`/tasks/${id}`);assert.equal(detail.learningDurationMinutes,45);assert.equal(detail.totalProgress,100);
 assert.equal((await api('GET',`/check-ins/tasks/${id}/stats`)).completionRate,33.3);
 const removable=await api('POST','/actions/deferred',{taskId:id,content:'结束后待清理的描述',durationMinutes:5,requestId:crypto.randomUUID()});
 await api('PUT',`/tasks/${id}`,{version:detail.version,status:'ENDED'});
 const pending=await api('GET',`/actions/deferred?taskId=${id}`);assert.equal(pending.list[0].id,removable.id);assert.equal(pending.list[0].canRetry,false);
 await api('POST',`/actions/deferred/${removable.id}/retry`,undefined,400);
 await api('DELETE',`/actions/deferred/${removable.id}`);
 assert.equal((await api('GET',`/actions/deferred?taskId=${id}`)).total,0);
 detail=await api('GET',`/tasks/${id}`);assert.equal(detail.totalProgress,100);assert.equal(detail.learningDurationMinutes,45);assert.equal(detail.status,'ENDED');
 console.log(JSON.stringify({passed:true,manualCompletionSurvivesUndo:true,originalDatePreserved:true,correctedRetryMinutes:45,repeatedRetryRejected:true,attendanceRate:33.3,closedPlanRetryBlocked:true,closedPendingRemovable:true}));
}finally{if(id)await api('DELETE',`/tasks/${id}`);}
