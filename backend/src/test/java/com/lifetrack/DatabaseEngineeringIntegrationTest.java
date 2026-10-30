package com.lifetrack;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.*;
import com.lifetrack.entity.*;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.*;
import com.lifetrack.service.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** MySQL 真实事务、并发和查询次数验证；数据限定为测试创建的用户。 */
@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true", "logging.level.org.hibernate=ERROR"})
@ActiveProfiles("test")
class DatabaseEngineeringIntegrationTest {
    @Autowired UserRepository users;
    @Autowired TaskRepository tasks;
    @Autowired SubTaskRepository steps;
    @Autowired CheckInRecordRepository checkIns;
    @Autowired TaskService plans;
    @Autowired CheckInService attendance;
    @Autowired ActionService actions;
    @Autowired DeferredLearningRecordService deferred;
    @Autowired EntityManagerFactory emf;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.password}") String testPassword;
    @MockitoBean AIService ai;
    User user;
    Long planId;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

    @BeforeEach void setup() {
        user = users.saveAndFlush(User.builder().username("db-test-"+UUID.randomUUID()).password("TEST_ONLY").build());
        UserContext.setUserId(user.getId());
        planId = create();
    }
    Long create() {
        var request = new ManualTaskCreateRequest();
        request.setTitle("数据库并发验收"); request.setCategory("学习");
        request.setStartDate(today.minusDays(2)); request.setEndDate(today.plusDays(5)); request.setTargetDays(5);
        var step = new ManualTaskCreateRequest.SubTaskDTO(); step.setContent("学习数据库事务与锁"); step.setWeight(BigDecimal.ONE);
        request.setSubTasks(List.of(step));
        return plans.createManualTask(request).getTaskId();
    }
    @AfterEach void cleanup() {
        UserContext.setUserId(user.getId());
        try { for (Task t : tasks.findByUserId(user.getId())) plans.deleteTask(t.getId()); users.deleteById(user.getId()); }
        finally { UserContext.clear(); }
    }
    ActionSyncRequest request() {
        var r = new ActionSyncRequest(); r.setTaskId(planId); r.setRawInput("整理了数据库事务与锁的知识笔记"); r.setDurationMinutes(30); return r;
    }
    AIMatchResult analysis() {
        Long step = steps.findByTaskIdOrderByOrderNumAsc(planId).get(0).getId();
        return AIMatchResult.builder().durationMinutes(30).matches(List.of(AIMatchResult.MatchDetail.builder()
                .subTaskId(step).increment(new BigDecimal("0.20")).build())).build();
    }

    @Test void undoOldAiRecordPreservesLaterManualCompletionAndRealAttendance() {
        when(ai.analyzeAction(anyString(),anyString(),anyList())).thenReturn(analysis());
        deferred.submit(request());
        long log = jdbc.queryForObject("select id from action_logs where task_id=?", Long.class, planId);
        long step = steps.findByTaskIdOrderByOrderNumAsc(planId).get(0).getId();
        plans.completeSubTask(step);
        assertNull(jdbc.queryForObject("select sub_task_id from action_logs where id=?", Long.class, log));
        actions.deleteAction(log);
        assertEquals(0, steps.findById(step).orElseThrow().getCurrentProgress().compareTo(BigDecimal.ONE));
        assertEquals(1, steps.findById(step).orElseThrow().getIsCompleted());
        assertEquals(0, tasks.findById(planId).orElseThrow().getTotalProgress().compareTo(BigDecimal.ONE));
        assertEquals(PlanStatus.IN_PROGRESS, tasks.findById(planId).orElseThrow().getStatus());
        assertEquals(0, attendance.stats(planId).getCheckedInDays());
        assertEquals(0, plans.getTaskDetail(planId).getLearningDurationMinutes());
    }

    @Test void dailyAggregationUsesHalfOpenShanghaiDayAndKeepsManualNotes() {
        var manual = new CheckInCreateRequest(); manual.setTaskId(planId); attendance.create(manual);
        jdbc.update("update check_in_records set content='保留手动备注' where task_id=?", planId);
        LocalDateTime[] times = {today.minusDays(1).atTime(23,59,59), today.atStartOfDay(),
                today.atTime(23,59,59), today.plusDays(1).atStartOfDay()};
        int[] minutes = {900, 30, 40, 800};
        for (int i=0;i<times.length;i++) jdbc.update("insert into action_logs (user_id,task_id,category,raw_input,duration_minutes,contribution,created_at) values (?,?,'学习','边界数据',?,0,?)",
                user.getId(), planId, minutes[i], times[i]);
        actions.refreshAttendance(planId, today);
        assertEquals(70, jdbc.queryForObject("select duration_minutes from check_in_records where task_id=?", Integer.class, planId));
        assertEquals("保留手动备注", jdbc.queryForObject("select content from check_in_records where task_id=?", String.class, planId));
        assertEquals(1770, plans.getTaskDetail(planId).getLearningDurationMinutes());
    }

    @Test void editingWhileAiIsRunningDiscardsStaleAnalysisWithoutAnyProgress() throws Exception {
        var input = new DeferredLearningRecordRequest(); input.setTaskId(planId); input.setContent(request().getRawInput()); input.setDurationMinutes(30);
        long record = deferred.save(input).id();
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        var result = analysis();
        when(ai.analyzeAction(anyString(),anyString(),anyList())).thenAnswer(call -> { started.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return result; });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> { UserContext.setUserId(user.getId());
                try { deferred.retry(record); return 200; } catch(BusinessException e) { return e.getCode(); } finally { UserContext.clear(); } });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            var edit = new DeferredLearningRecordUpdateRequest(); edit.setContent("修正后的事务练习"); edit.setDurationMinutes(45);
            var replacement = deferred.update(record, edit);
            release.countDown();
            assertEquals(409, future.get(10, TimeUnit.SECONDS));
            assertEquals("PENDING", jdbc.queryForObject("select status from deferred_learning_records where id=?", String.class, replacement.id()));
            assertEquals(0, jdbc.queryForObject("select count(*) from action_logs where task_id=?", Integer.class, planId));
            assertEquals(0, jdbc.queryForObject("select count(*) from check_in_records where task_id=?", Integer.class, planId));
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void learningFirstAllowsReviewUntilLastAttendanceAndThenCloses() {
        plans.completeSubTask(steps.findByTaskIdOrderByOrderNumAsc(planId).get(0).getId());
        assertEquals(PlanStatus.IN_PROGRESS,tasks.findById(planId).orElseThrow().getStatus());
        when(ai.analyzeAction(anyString(),anyString(),anyList())).thenReturn(analysis());
        var input=request(); input.setRequestId(UUID.randomUUID().toString());
        var result=deferred.submit(input);
        assertEquals(30,result.getDurationMinutes());
        assertEquals(1,result.getSavedRecordCount());
        assertEquals(0,result.getUpdates().get(0).getIncrement().signum());
        assertEquals(0,tasks.findById(planId).orElseThrow().getTotalProgress().compareTo(BigDecimal.ONE));
        assertTrue(deferred.submit(input).isReplayed());
        for(int i=1;i<5;i++) {
            jdbc.update("insert into check_in_records (user_id,task_id,check_in_date,content,duration_minutes,automatic,created_at) values (?,?,?,?,0,false,now())",user.getId(),planId,today.minusDays(i),"");
        }
        // Only dates within the plan count: expand the test period before checking the final day.
        jdbc.update("update tasks set start_date=? where id=?",today.minusDays(5),planId);
        // Recalculate via a normal update of the attendance target.
        var edit=new TaskUpdateRequest();edit.setVersion(tasks.findById(planId).orElseThrow().getVersion());edit.setTargetDays(6);
        plans.updateTask(planId,edit);
        var finalDay=new CheckInCreateRequest();finalDay.setTaskId(planId);finalDay.setCheckInDate(today.minusDays(5));
        attendance.create(finalDay);
        assertEquals(PlanStatus.COMPLETED,tasks.findById(planId).orElseThrow().getStatus());
        assertThrows(BusinessException.class,()->deferred.submit(request()));
        assertThrows(BusinessException.class,()->attendance.create(finalDay));
    }

    @Test void attendanceFirstCanExceedTargetUntilLearningCompletes() {
        jdbc.update("update tasks set start_date=? where id=?",today.minusDays(6),planId);
        for(int i=0;i<6;i++) {
            var r=new CheckInCreateRequest();r.setTaskId(planId);r.setCheckInDate(today.minusDays(i));attendance.create(r);
        }
        assertEquals(PlanStatus.IN_PROGRESS,tasks.findById(planId).orElseThrow().getStatus());
        when(ai.analyzeAction(anyString(),anyString(),anyList())).thenReturn(analysis());
        assertEquals(30,deferred.submit(request()).getDurationMinutes());
        plans.completeSubTask(steps.findByTaskIdOrderByOrderNumAsc(planId).get(0).getId());
        assertEquals(PlanStatus.COMPLETED,tasks.findById(planId).orElseThrow().getStatus());
        assertEquals(6,attendance.stats(planId).getCheckedInDays());
    }

    @Test void loweringAttendanceTargetCanCompleteLearningButCannotForceIncompleteLearning() {
        var r=new CheckInCreateRequest();r.setTaskId(planId);attendance.create(r);
        var edit=new TaskUpdateRequest();edit.setVersion(tasks.findById(planId).orElseThrow().getVersion());edit.setStatus("COMPLETED");
        assertThrows(BusinessException.class,()->plans.updateTask(planId,edit));
        plans.completeSubTask(steps.findByTaskIdOrderByOrderNumAsc(planId).get(0).getId());
        edit.setVersion(tasks.findById(planId).orElseThrow().getVersion());
        assertThrows(BusinessException.class,()->plans.updateTask(planId,edit));
        edit.setStatus(null);edit.setTargetDays(1);
        assertEquals("COMPLETED",plans.updateTask(planId,edit).getStatus());
    }

    @Test void v4ReopensOnlyOldLearningOnlyCompletionAndPreservesHistory() {
        Long both=create(),ended=create(),expired=create(),future=create();
        for(Long id:List.of(planId,both,expired,future)) {
            jdbc.update("update tasks set status=1,total_progress=1 where id=?",id);
            jdbc.update("update sub_tasks set current_progress=1,is_completed=1 where task_id=?",id);
        }
        jdbc.update("update tasks set target_days=1 where id=?",both);
        jdbc.update("insert into check_in_records (user_id,task_id,check_in_date,content,duration_minutes,automatic,created_at) values (?,?,?,?,0,false,now())",user.getId(),both,today,"history");
        jdbc.update("update tasks set status=5 where id=?",ended);
        jdbc.update("update tasks set start_date=?,end_date=? where id=?",today.minusDays(8),today.minusDays(1),expired);
        jdbc.update("update tasks set start_date=?,end_date=? where id=?",today.plusDays(1),today.plusDays(8),future);
        long version=tasks.findById(planId).orElseThrow().getVersion();
        var sql=new org.springframework.core.io.ClassPathResource("db/migration/V4__complete_only_when_learning_and_attendance_reached.sql");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(sql).execute(dataSource);
        assertEquals(PlanStatus.IN_PROGRESS,tasks.findById(planId).orElseThrow().getStatus());
        assertEquals(version+1,tasks.findById(planId).orElseThrow().getVersion());
        assertEquals(PlanStatus.COMPLETED,tasks.findById(both).orElseThrow().getStatus());
        assertEquals(PlanStatus.ENDED,tasks.findById(ended).orElseThrow().getStatus());
        assertEquals(PlanStatus.EXPIRED,tasks.findById(expired).orElseThrow().getStatus());
        assertEquals(PlanStatus.NOT_STARTED,tasks.findById(future).orElseThrow().getStatus());
        assertEquals("history",checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(user.getId(),both).get(0).getContent());
        assertEquals(0,steps.findByTaskIdOrderByOrderNumAsc(planId).get(0).getCurrentProgress().compareTo(BigDecimal.ONE));
    }

    @Test void simultaneousAttendanceHasExactlyOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> submit = () -> {
            UserContext.setUserId(user.getId());
            try {
                start.await(); var r = new CheckInCreateRequest(); r.setTaskId(planId); attendance.create(r); return 200;
            } catch (BusinessException e) { return e.getCode(); }
            finally { UserContext.clear(); }
        };
        try {
            Future<Integer> a = pool.submit(submit), b = pool.submit(submit); start.countDown();
            assertEquals(List.of(200,409), java.util.stream.Stream.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)).sorted().toList());
            assertEquals(1, checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(user.getId(),planId).size());
        } finally { pool.shutdownNow(); }
    }

    @Test void lostSuccessResponseCanBeReplayedWithoutAnotherAnalysisOrDuration() {
        var request=request(); request.setRequestId(UUID.randomUUID().toString());
        when(ai.analyzeAction(anyString(),anyString(),anyList())).thenReturn(analysis());
        var first=deferred.submit(request); var repeated=deferred.submit(request);
        assertTrue(repeated.isReplayed()); assertEquals(first.getRecordId(),repeated.getRecordId());
        verify(ai,times(1)).analyzeAction(anyString(),anyString(),anyList());
        assertEquals(1,jdbc.queryForObject("select count(*) from deferred_learning_records where task_id=?",Integer.class,planId));
        assertEquals(30,jdbc.queryForObject("select sum(duration_minutes) from action_logs where task_id=?",Integer.class,planId));
        assertEquals(1,jdbc.queryForObject("select count(*) from check_in_records where task_id=?",Integer.class,planId));
    }

    @Test void manualAttendanceSurvivesLearningSaveRetryAndUndoWithoutStaleMinutes() {
        var manual=new CheckInCreateRequest(); manual.setTaskId(planId); attendance.create(manual);
        jdbc.update("update check_in_records set content=? where task_id=?","旧手动备注",planId);
        var input=new DeferredLearningRecordRequest();input.setTaskId(planId);input.setContent(request().getRawInput());input.setDurationMinutes(30);
        long record=deferred.save(input).id();
        when(ai.analyzeAction(anyString(),anyString(),anyList())).thenReturn(analysis());
        deferred.retry(record);
        long log=jdbc.queryForObject("select id from action_logs where task_id=?",Long.class,planId);
        actions.deleteAction(log);
        assertEquals(1,jdbc.queryForObject("select count(*) from check_in_records where task_id=?",Integer.class,planId));
        assertEquals(0,jdbc.queryForObject("select duration_minutes from check_in_records where task_id=?",Integer.class,planId));
        assertEquals("旧手动备注",jdbc.queryForObject("select content from check_in_records where task_id=?",String.class,planId));
        assertEquals(0,plans.getTaskDetail(planId).getLearningDurationMinutes());
    }

    @Test void oldPendingRecordCannotRecreateAttendanceAfterPeriodMovesForward() {
        var input=new DeferredLearningRecordRequest();input.setTaskId(planId);input.setContent(request().getRawInput());input.setDurationMinutes(30);
        long record=deferred.save(input).id();
        var update=new TaskUpdateRequest();update.setVersion(plans.getTaskDetail(planId).getVersion());update.setStartDate(today.plusDays(1));
        plans.updateTask(planId,update);
        assertThrows(BusinessException.class,()->deferred.retry(record));
        assertFalse(checkIns.existsByUserIdAndTaskIdAndCheckInDate(user.getId(),planId,today));
        assertEquals("PENDING",jdbc.queryForObject("select status from deferred_learning_records where id=?",String.class,record));
        verifyNoInteractions(ai);
    }

    @Test void periodEditAndAttendanceRaceNeverLeavesAnOutsideRecord() throws Exception {
        var input=new TaskUpdateRequest();input.setVersion(plans.getTaskDetail(planId).getVersion());input.setStartDate(today.plusDays(1));
        var ready=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Integer> check=()->{UserContext.setUserId(user.getId());try{ready.await();var r=new CheckInCreateRequest();r.setTaskId(planId);attendance.create(r);return 200;}catch(BusinessException e){return e.getCode();}finally{UserContext.clear();}};
        Callable<Integer> edit=()->{UserContext.setUserId(user.getId());try{ready.await();plans.updateTask(planId,input);return 200;}catch(BusinessException e){return e.getCode();}finally{UserContext.clear();}};
        try {
            var a=pool.submit(check);var b=pool.submit(edit);ready.countDown();
            int ca=a.get(10,TimeUnit.SECONDS),cb=b.get(10,TimeUnit.SECONDS);
            assertTrue(ca==200 && cb==409 || ca==400 && cb==200,"竞争必须由锁和服务端重新校验决定");
            var plan=tasks.findById(planId).orElseThrow();
            assertTrue(checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(user.getId(),planId).stream()
                    .allMatch(r->!r.getCheckInDate().isBefore(plan.getStartDate()) && !r.getCheckInDate().isAfter(plan.getEndDate())));
        } finally {pool.shutdownNow();}
    }

    @Test void repeatedWeightEditsRemainPositiveNormalizedAndRollbackIllegalChanges() {
        for(int i=0;i<4;i++){var add=new SubTaskCreateRequest();add.setContent("压力学习步骤"+i);add.setWeight(new BigDecimal("0.20"));plans.addSubTask(planId,add);}
        for(int i=0;i<60;i++) {
            var current=steps.findByTaskIdOrderByOrderNumAsc(planId);var update=new SubTaskUpdateRequest();update.setVersion(tasks.findById(planId).orElseThrow().getVersion());update.setWeight(BigDecimal.valueOf((i%9+1)/10.0));
            try {plans.updateSubTask(current.get(i%current.size()).getId(),update);}catch(BusinessException e){assertEquals(400,e.getCode());}
            var saved=steps.findByTaskIdOrderByOrderNumAsc(planId);
            assertTrue(saved.stream().allMatch(s->s.getWeight().compareTo(new BigDecimal("0.01"))>=0));
            assertEquals(0,saved.stream().map(SubTask::getWeight).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(BigDecimal.ONE));
        }
    }

    @Test void migrationPreservesLegacyHistoryAndSeparatesFalseCompletedPlans() throws Exception {
        Long genuinelyComplete = create();
        jdbc.update("update tasks set status=1 where id in (?,?)", planId, genuinelyComplete);
        jdbc.update("update sub_tasks set current_progress=1,is_completed=1 where task_id=?", genuinelyComplete);
        jdbc.update("update tasks set total_progress=1 where id=?", genuinelyComplete);
        // 历史数据夹具：直接写入已有打卡，不绕过实际打卡接口规则去新增终态打卡。
        jdbc.update("insert into check_in_records (user_id,task_id,check_in_date,content,duration_minutes,automatic,created_at) values (?,?,?,?,0,false,now())",
                user.getId(), planId, today, "保留旧备注");
        var sql = new org.springframework.core.io.ClassPathResource("db/migration/V2__separate_manual_end_from_completion.sql");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(sql).execute(dataSource);
        assertEquals(5, jdbc.queryForObject("select status from tasks where id=?", Integer.class, planId));
        assertEquals(1, jdbc.queryForObject("select status from tasks where id=?", Integer.class, genuinelyComplete));
        assertEquals("保留旧备注", jdbc.queryForObject("select content from check_in_records where task_id=?", String.class, planId));
        assertEquals(1, jdbc.queryForObject("select count(*) from sub_tasks where task_id=?", Integer.class, planId));
    }

    @Test void failedLearningRecordDoesNotCountUntilRetryAndNeverOverwritesManualNote() {
        var manual = new CheckInCreateRequest(); manual.setTaskId(planId);
        attendance.create(manual);
        // 模拟已有的历史手动备注，确认新规则不会覆盖历史正文。
        jdbc.update("update check_in_records set content=? where task_id=?", "历史备注保留", planId);
        when(ai.analyzeAction(anyString(), anyString(), anyList())).thenReturn(
                AIMatchResult.builder().unavailable(true).matches(List.of()).aiAnalysis("暂不可用").build());
        var failed = deferred.submit(request());
        assertNotNull(failed.getRecordId());
        assertEquals(0, plans.getTaskDetail(planId).getLearningDurationMinutes());
        assertEquals(0, jdbc.queryForObject("select count(*) from action_logs where task_id=?", Integer.class, planId));
        assertEquals(0, jdbc.queryForObject("select duration_minutes from check_in_records where task_id=?", Integer.class, planId));
        var success = analysis(); success.setDurationMinutes(999); // 故意让模型报错时长，后端仍使用用户的30分钟。
        when(ai.analyzeAction(anyString(), anyString(), anyList())).thenReturn(success);
        assertEquals(30, deferred.retry(failed.getRecordId()).getDurationMinutes());
        assertEquals(30, plans.getTaskDetail(planId).getLearningDurationMinutes());
        assertEquals(30, jdbc.queryForObject("select sum(duration_minutes) from action_logs where task_id=?", Integer.class, planId));
        assertEquals(30, jdbc.queryForObject("select duration_minutes from check_in_records where task_id=?", Integer.class, planId));
        assertEquals("历史备注保留", jdbc.queryForObject("select content from check_in_records where task_id=?", String.class, planId));
        assertEquals(409, assertThrows(BusinessException.class, () -> deferred.retry(failed.getRecordId())).getCode());
        assertEquals(30, jdbc.queryForObject("select sum(duration_minutes) from action_logs where task_id=?", Integer.class, planId));
    }

    @Test void concurrentRetriesCountOriginalDurationExactlyOnce() throws Exception {
        var input = new DeferredLearningRecordRequest(); input.setTaskId(planId);
        input.setContent(request().getRawInput()); input.setDurationMinutes(75);
        long record = deferred.save(input).id();
        assertFalse(checkIns.existsByUserIdAndTaskIdAndCheckInDate(user.getId(), planId, today));
        var response = analysis();
        var bothInAi = new CountDownLatch(2);
        when(ai.analyzeAction(anyString(), anyString(), anyList())).thenAnswer(i -> {
            bothInAi.countDown(); assertTrue(bothInAi.await(10, TimeUnit.SECONDS)); return response;
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> retry = () -> {
            UserContext.setUserId(user.getId());
            try { deferred.retry(record); return 200; }
            catch (BusinessException e) { return e.getCode(); }
            finally { UserContext.clear(); }
        };
        try {
            var a = pool.submit(retry); var b = pool.submit(retry);
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)).sorted().toList());
            assertEquals(75, jdbc.queryForObject("select sum(duration_minutes) from action_logs where task_id=?", Integer.class, planId));
            assertEquals(1, jdbc.queryForObject("select count(*) from check_in_records where task_id=?", Integer.class, planId));
        } finally { pool.shutdownNow(); }
    }

    @Test void aiRunsWithoutTransactionAndChangedPlanRejectsStaleResult() {
        var response = analysis();
        when(ai.analyzeAction(anyString(),anyString(),anyList())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            var change = new TaskUpdateRequest(); change.setVersion(plans.getTaskDetail(planId).getVersion()); change.setTitle("AI分析过程中修改标题"); plans.updateTask(planId,change);
            return response;
        });
        var ex = assertThrows(BusinessException.class, () -> actions.syncAction(request()));
        assertEquals(409,ex.getCode());
        assertEquals(0, steps.findByTaskIdOrderByOrderNumAsc(planId).get(0).getCurrentProgress().signum());
        assertFalse(checkIns.existsByUserIdAndTaskIdAndCheckInDate(user.getId(),planId,today));
    }

    @Test void slowAiDoesNotBlockManualAttendance() throws Exception {
        var response = analysis();
        CountDownLatch inAi = new CountDownLatch(1), release = new CountDownLatch(1);
        when(ai.analyzeAction(anyString(),anyString(),anyList())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            inAi.countDown(); assertTrue(release.await(10,TimeUnit.SECONDS)); return response;
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ActionSyncResponse> f = pool.submit(() -> { UserContext.setUserId(user.getId()); try { return actions.syncAction(request()); } finally { UserContext.clear(); } });
            assertTrue(inAi.await(5,TimeUnit.SECONDS));
            Future<?> manual = pool.submit(() -> { UserContext.setUserId(user.getId()); try { var r=new CheckInCreateRequest();r.setTaskId(planId);attendance.create(r); } finally { UserContext.clear(); } });
            manual.get(3,TimeUnit.SECONDS);
            release.countDown(); assertEquals(1,f.get(10,TimeUnit.SECONDS).getSavedRecordCount());
            assertEquals(1,checkIns.findByUserIdAndTaskIdOrderByCheckInDateAsc(user.getId(),planId).size());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void retryAnalyzesOutsideTransactionAndAppliesOriginalRecordExactlyOnce() {
        var r = new DeferredLearningRecordRequest();r.setTaskId(planId);r.setContent("整理了数据库事务与锁的知识笔记");r.setDurationMinutes(30);
        var saved = deferred.save(r);
        var result=analysis();
        when(ai.analyzeAction(anyString(),anyString(),anyList())).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return result;
        });
        assertEquals(1,deferred.retry(saved.id()).getSavedRecordCount());
        assertEquals(409,assertThrows(BusinessException.class,()->deferred.retry(saved.id())).getCode());
    }

    @Test void filteredPlanListUsesTwoQueriesAndDoesNotWrite() {
        for(int i=0;i<12;i++) create();
        var stats=emf.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        var list=plans.getTaskList("IN_PROGRESS",today,today);
        assertEquals(13,list.size());
        assertTrue(list.stream().allMatch(p->p.getSubtaskCount()==1));
        assertEquals(2,stats.getPrepareStatementCount(),"一条计划查询、一条分组计数，不能逐计划查询");
        assertEquals(0,stats.getEntityUpdateCount());
    }

    @Test void databaseRejectsInvalidPeriodAndCrossUserAttendanceDirectly() {
        var period=assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update tasks set end_date=start_date - interval 1 day where id=?",planId));
        assertEquals(3819,((java.sql.SQLException)period.getMostSpecificCause()).getErrorCode());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("insert into check_in_records(user_id,task_id,check_in_date,content,duration_minutes,created_at) values(?,?,?,?,?,now())",-1,planId,today,"",0));
        var progress=assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update sub_tasks set current_progress=1.5 where task_id=?",planId));
        assertEquals(3819,((java.sql.SQLException)progress.getMostSpecificCause()).getErrorCode());
    }

    @Test void optimisticVersionRejectsDetachedStaleWrite() {
        Task first=tasks.findById(planId).orElseThrow(), stale=tasks.findById(planId).orElseThrow();
        first.setTitle("先提交的修改"); tasks.saveAndFlush(first);
        stale.setTitle("过时的副本");
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->tasks.saveAndFlush(stale));
        assertEquals("先提交的修改",tasks.findById(planId).orElseThrow().getTitle());
    }

    @Test void consolidatedSchemaInitializesEmptyMysqlAndFlywayManagesOnlySubsequentVersions() throws Exception {
        String name = "lifetrack_acceptance_" + UUID.randomUUID().toString().replace("-", "");
        var folder = java.nio.file.Files.createTempDirectory("lifetrack-flyway-test-");
        try (var c = dataSource.getConnection()) {
            String original = c.getCatalog();
            String url = c.getMetaData().getURL().replace("/"+original+"?", "/"+name+"?");
            String userName=c.getMetaData().getUserName().split("@")[0];
            String password=testPassword;
            String sql=java.nio.file.Files.readString(java.nio.file.Path.of("sql/init_db.sql")).replace("lifetrack",name);
            var resource=new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,resource);
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,resource);
            assertEquals(7,new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(c,true))
                    .queryForObject("select count(*) from information_schema.TABLES where TABLE_SCHEMA=?",Integer.class,name));
            var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,userName,password)
                    .locations("filesystem:"+folder).baselineVersion("1").cleanDisabled(true).load();
            assertThrows(org.flywaydb.core.api.FlywayException.class,flyway::migrate,"未登记基线的非空库必须拒绝静默接管");
            flyway.baseline();
            var migration=folder.resolve("V2__acceptance_probe.sql");
            java.nio.file.Files.writeString(migration,"CREATE TABLE acceptance_probe (id bigint PRIMARY KEY);");
            assertEquals(1,flyway.migrate().migrationsExecuted);
            assertEquals(0,flyway.migrate().migrationsExecuted,"已执行版本不能重复运行");
            java.nio.file.Files.writeString(migration,"CREATE TABLE acceptance_probe (id int PRIMARY KEY);");
            assertThrows(org.flywaydb.core.api.FlywayException.class,flyway::validate,"已执行脚本被修改必须报校验失败");
            c.setCatalog(original);
        } finally {
            // 仅销毁本测试随机创建的隔离库，不触碰应用数据库。
            if (!name.matches("lifetrack_acceptance_[a-f0-9]{32}")) throw new IllegalStateException();
            jdbc.execute("DROP DATABASE IF EXISTS `"+name+"`");
            java.nio.file.Files.deleteIfExists(folder.resolve("V2__acceptance_probe.sql"));
            java.nio.file.Files.deleteIfExists(folder);
        }
    }
}
