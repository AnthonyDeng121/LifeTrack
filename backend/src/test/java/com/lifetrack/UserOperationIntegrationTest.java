package com.lifetrack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetrack.entity.*;
import com.lifetrack.repository.*;
import com.lifetrack.util.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 HTTP、鉴权与 MySQL 集成验证；仅创建并清理本测试用户的数据，不调用模型。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ai.base-url=http://127.0.0.1:1/ai")
@ActiveProfiles("test")
class UserOperationIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired ActionLogRepository logs;
    @Autowired JwtUtil jwt;
    @Autowired org.springframework.jdbc.core.JdbcTemplate database;
    User user;
    String token;
    LocalDate today;
    final List<Long> taskIds = new ArrayList<>();
    final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach void prepare() {
        user = users.saveAndFlush(User.builder().username("flow-test-" + UUID.randomUUID()).password("TEST_ONLY").build());
        token = jwt.generateToken(user.getId(), user.getUsername());
        today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    }
    @AfterEach void cleanup() throws Exception {
        try {
            for (Long id : taskIds) assertEquals(200, api("DELETE", "/tasks/" + id, null).path("code").asInt());
        } finally { if (user != null) users.deleteById(user.getId()); }
    }
    JsonNode api(String method, String path, Object body) throws Exception {
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15)).method(method, publisher).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode result = mapper.readTree(response.body());
        assertEquals(result.path("code").asInt(), response.statusCode(), "HTTP状态必须与业务码一致");
        return result;
    }
    long create(LocalDate start, LocalDate end) throws Exception {
        var response = api("POST", "/tasks/manual", Map.of("title", "用户操作集成测试", "category", "学习",
                "startDate", start.toString(), "endDate", end.toString(), "targetDays", 5,
                "subTasks", List.of(Map.of("content", "学习数据库", "weight", 1))));
        long id = response.path("data").path("taskId").asLong();
        if (id > 0) taskIds.add(id);
        assertEquals(200, response.path("code").asInt(), response.toString());
        return id;
    }

    @Test void stepContentChangeRequiresExplicitProgressPolicyAndKeepsHistoryWithoutOldProgress() throws Exception {
        long id = create(today, today.plusDays(7));
        long step = api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0).path("id").asLong();
        assertEquals(200, api("PUT", "/tasks/subtasks/" + step + "/complete", null).path("code").asInt());
        long version = api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0).path("version").asLong();
        var oldLog = logs.saveAndFlush(ActionLog.builder().userId(user.getId()).taskId(id).subTaskId(step)
                .rawInput("旧内容学习记录").durationMinutes(30).contribution(new java.math.BigDecimal("0.20"))
                .category(Task.Category.学习).build());
        assertEquals(400, api("PUT", "/tasks/subtasks/" + step, Map.of("version", version, "content", "全新内容")).path("code").asInt());
        assertEquals(1, database.queryForObject("select is_completed from sub_tasks where id=?", Integer.class, step));
        assertEquals(200, api("PUT", "/tasks/subtasks/" + step,
                Map.of("version", version, "content", "学习数据库（文字修正）", "progressPolicy", "KEEP")).path("code").asInt());
        assertEquals(1, database.queryForObject("select is_completed from sub_tasks where id=?", Integer.class, step));
        long next = api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0).path("version").asLong();
        assertTrue(next > version, "仅文字变更也必须推进版本");
        assertEquals(409, api("PUT", "/tasks/subtasks/" + step,
                Map.of("version", version, "content", "旧窗口内容", "progressPolicy", "RESET")).path("code").asInt());
        assertEquals(200, api("PUT", "/tasks/subtasks/" + step,
                Map.of("version", next, "content", "全新的微积分学习", "progressPolicy", "RESET")).path("code").asInt());
        var updated = api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0);
        assertEquals(0, updated.path("isCompleted").asInt());
        assertEquals(0, updated.path("currentProgress").decimalValue().signum());
        assertEquals(30, api("GET", "/tasks/" + id, null).path("data").path("learningDurationMinutes").asInt());
        assertNull(database.queryForObject("select sub_task_id from action_logs where id=?", Long.class, oldLog.getId()));
        assertEquals("旧内容学习记录", logs.findById(oldLog.getId()).orElseThrow().getRawInput());
        // 新内容有了新进度后，撤销旧日志只撤销旧时长，不能扣除新内容进度。
        database.update("update sub_tasks set current_progress=0.30 where id=?", step);
        database.update("update tasks set total_progress=0.30 where id=?", id);
        assertEquals(200, api("DELETE", "/actions/" + oldLog.getId(), null).path("code").asInt());
        assertEquals(0, database.queryForObject("select current_progress from sub_tasks where id=?", java.math.BigDecimal.class, step)
                .compareTo(new java.math.BigDecimal("0.30")));
    }

    @Test void staleStepEditorCannotOverwriteEvenWhenLearningProgressDoesNotChange() throws Exception {
        long id = create(today, today.plusDays(7));
        var step = api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0);
        String path = "/tasks/subtasks/" + step.path("id").asLong();
        long version = step.path("version").asLong();
        assertEquals(400, api("PUT", path, Map.of("content", "缺版本")).path("code").asInt());
        assertEquals(200, api("PUT", path, Map.of("version", version, "content", "窗口B的新内容")).path("code").asInt());
        assertEquals(409, api("PUT", path, Map.of("version", version, "content", "窗口A的旧内容")).path("code").asInt());
        assertEquals("窗口B的新内容", api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0).path("content").asText());
    }

    @Test void unsupportedHistorySortIsAClientErrorAndValidSortStillWorks() throws Exception {
        assertEquals(400, api("GET", "/actions/history?sort=nonexistent,asc", null).path("code").asInt());
        assertEquals(400, api("GET", "/actions/history?sort=createdAt,desc&sort=rawInput,asc", null).path("code").asInt());
        assertEquals(200, api("GET", "/actions/history?sort=durationMinutes,asc&sort=id,desc", null).path("code").asInt());
    }

    @Test void databaseDateBoundsAreRejectedBeforePersistenceAcrossPlanInputs() throws Exception {
        for (String[] period : List.of(new String[]{"0999-12-31", "1000-01-06"}, new String[]{"9999-12-31", "+10000-01-01"})) {
            var response = api("POST", "/tasks/manual", Map.of("title", "非法年份", "category", "学习",
                    "startDate", period[0], "endDate", period[1], "targetDays", 1,
                    "subTasks", List.of(Map.of("content", "测试", "weight", 1))));
            assertEquals(400, response.path("code").asInt());
            assertTrue(response.path("message").asText().contains("1000到9999"));
        }
        assertEquals(0, database.queryForObject("select count(*) from tasks where user_id=?", Integer.class, user.getId()));
        long id = create(today, today.plusDays(7));
        long version = api("GET", "/tasks/" + id, null).path("data").path("version").asLong();
        assertEquals(400, api("PUT", "/tasks/" + id, Map.of("version", version, "endDate", "+10000-01-01")).path("code").asInt());
        assertEquals(400, api("GET", "/tasks/" + id + "/period-preview?startDate=0999-01-01&endDate=" + today, null).path("code").asInt());
        assertEquals(400, api("GET", "/tasks?endDate=%2B10000-01-01", null).path("code").asInt());
        assertEquals(400, api("POST", "/check-ins", Map.of("taskId", id, "checkInDate", "+10000-01-01")).path("code").asInt());
        assertEquals(400, api("GET", "/check-ins?startDate=0999-01-01", null).path("code").asInt());
        assertEquals(200, api("PUT", "/tasks/" + id, Map.of("version", version, "startDate", "1000-01-01", "endDate", "9999-12-31")).path("code").asInt());
    }

    @Test void learningSubmissionActivatesByBusinessDateWithoutWaitingForScheduledRefresh() throws Exception {
        long id = create(today, today.plusDays(7));
        // 模拟已跨入开始日，但分钟调度还未激活数据库状态。
        database.update("update tasks set status=2 where id=?", id);
        var input = Map.of("requestId", UUID.randomUUID().toString(), "taskId", id, "content", "学习数据库事务", "durationMinutes", 30);
        var saved = api("POST", "/actions/deferred", input);
        assertEquals(200, saved.path("code").asInt());
        assertEquals(0, database.queryForObject("select status from tasks where id=?", Integer.class, id));
        assertEquals(1, database.queryForObject("select count(*) from deferred_learning_records where task_id=?", Integer.class, id));
        for (int status : List.of(1, 3, 4, 5)) {
            database.update("update tasks set status=? where id=?", status, id);
            var blocked = new java.util.HashMap<String, Object>(input); blocked.put("requestId", UUID.randomUUID().toString());
            assertEquals(400, api("POST", "/actions/deferred", blocked).path("code").asInt(), "终态不可被日期归一化重新打开");
        }
        assertEquals(1, database.queryForObject("select count(*) from deferred_learning_records where task_id=?", Integer.class, id));
    }

    @Test void manualAttendanceRejectsDuplicateAndDateFiltersUsePersistedRecordsWithoutAI() throws Exception {
        long id = create(today.minusDays(2), today.plusDays(7));
        long future = create(today.plusDays(10), today.plusDays(20));
        for (LocalDate date : List.of(today.minusDays(1), today)) {
            var response = api("POST", "/check-ins", Map.of("taskId", id, "checkInDate", date.toString(), "content", "实际学习",
                    "durationMinutes", 30));
            assertEquals(200, response.path("code").asInt());
        }
        var duplicate = api("POST", "/check-ins", Map.of("taskId", id, "checkInDate", today.toString(), "content", "重复提交", "durationMinutes", 30));
        assertEquals(409, duplicate.path("code").asInt());
        var history = api("GET", "/check-ins?taskId=" + id + "&startDate=" + today + "&endDate=" + today, null);
        assertEquals(1, history.path("data").path("list").size());
        assertEquals(0, history.path("data").path("list").get(0).path("durationMinutes").asInt());
        assertEquals("", history.path("data").path("list").get(0).path("content").asText());
        assertEquals(2, api("GET", "/check-ins/tasks/" + id + "/stats", null).path("data").path("checkedInDays").asInt());
        assertEquals(0, api("GET", "/actions/history", null).path("data").path("total").asInt(), "正式打卡不会伪造AI学习记录");
        var plans = api("GET", "/tasks?status=IN_PROGRESS&startDate=" + today + "&endDate=" + today, null).path("data");
        assertEquals(1, plans.size()); assertEquals(id, plans.get(0).path("id").asLong());
        var noMatch = api("GET", "/tasks?startDate=" + today.plusDays(30), null).path("data");
        assertEquals(0, noMatch.size());
        assertEquals("NOT_STARTED", api("GET", "/tasks/" + future, null).path("data").path("status").asText());
        assertEquals(400, api("GET", "/check-ins?taskId=" + id + "&startDate=" + today + "&endDate=" + today.minusDays(1), null).path("code").asInt());
    }

    @Test void realAiConnectionFailureNeverCreatesFakeProgressAndFallbackRemainsDurable() throws Exception {
        long id=create(today.minusDays(2),today.plusDays(7));
        var preview=api("POST","/tasks/deconstruct/preview",Map.of("title","学习数据库事务","stepCount",3,
                "startDate",today.toString(),"endDate",today.plusDays(6).toString(),"targetDays",5));
        assertEquals(503,preview.path("code").asInt());
        assertEquals(1,database.queryForObject("select count(*) from tasks where user_id=?",Integer.class,user.getId()));
        var failed=api("POST","/actions/sync",Map.of("taskId",id,"rawInput","整理了数据库事务的学习笔记","durationMinutes",30,"requestId",UUID.randomUUID().toString()));
        assertEquals(200,failed.path("code").asInt());
        assertTrue(failed.path("data").path("analysisUnavailable").asBoolean());
        assertEquals(0,database.queryForObject("select count(*) from action_logs where task_id=?",Integer.class,id));
        assertEquals(0,database.queryForObject("select count(*) from check_in_records where task_id=?",Integer.class,id));
        long record=failed.path("data").path("recordId").asLong();
        assertTrue(record > 0);
        assertEquals("PENDING",database.queryForObject("select status from deferred_learning_records where id=?",String.class,record));
        assertEquals(0,database.queryForObject("select count(*) from check_in_records where task_id=?",Integer.class,id));
        assertTrue(api("POST","/actions/deferred/"+record+"/retry",Map.of()).path("data").path("analysisUnavailable").asBoolean());
        assertEquals("PENDING",database.queryForObject("select status from deferred_learning_records where id=?",String.class,record));
        assertEquals(0,database.queryForObject("select total_progress from tasks where id=?",BigDecimal.class,id).signum());
        assertEquals(0,database.queryForObject("select count(*) from check_in_records where task_id=?",Integer.class,id));
        assertEquals(30,database.queryForObject("select duration_minutes from deferred_learning_records where id=?",Integer.class,record));
    }

    @Test void staleFormCannotOverwriteNewerPlanAndMissingVersionIsRejected() throws Exception {
        long id = create(today, today.plusDays(7));
        long version = api("GET", "/tasks/" + id, null).path("data").path("version").asLong();
        var first = api("PUT", "/tasks/" + id, Map.of("title", "新窗口修改", "version", version));
        assertEquals(200, first.path("code").asInt());
        assertTrue(first.path("data").path("version").asLong() > version);
        assertEquals(409, api("PUT", "/tasks/" + id, Map.of("title", "旧窗口覆盖", "version", version)).path("code").asInt());
        assertEquals(400, api("PUT", "/tasks/" + id, Map.of("title", "无版本覆盖")).path("code").asInt());
        assertEquals("新窗口修改", api("GET", "/tasks/" + id, null).path("data").path("title").asText());
    }

    @Test void manualEndLearningCompletionAndAttendanceAreIndependent() throws Exception {
        long id = create(today, today.plusDays(7));
        assertEquals(400, api("PUT", "/tasks/" + id, Map.of("status", "COMPLETED", "version", 0)).path("code").asInt());
        // 用真实打卡达到目标，不允许因此伪造学习完成。
        database.update("update tasks set target_days=1 where id=?", id);
        assertEquals(200, api("POST", "/check-ins", Map.of("taskId", id)).path("code").asInt());
        assertEquals(100, api("GET", "/check-ins/tasks/" + id + "/stats", null).path("data").path("completionRate").asInt());
        assertEquals("IN_PROGRESS", api("GET", "/tasks/" + id, null).path("data").path("status").asText());
        assertEquals(200, api("PUT", "/tasks/" + id, Map.of("status", "ENDED", "version", 0)).path("code").asInt());
        var detail = api("GET", "/tasks/" + id, null).path("data");
        assertEquals(0, detail.path("totalProgress").asInt());
        assertEquals(0, api("GET", "/dashboard/stats", null).path("data").path("completedPlanCount").asInt());
        assertEquals(1, api("GET", "/tasks?status=ENDED", null).path("data").size());
        assertEquals(400, api("PUT", "/tasks/" + id, Map.of("status", "IN_PROGRESS", "version", detail.path("version").asLong())).path("code").asInt());
        assertEquals(400, api("POST", "/check-ins", Map.of("taskId", id)).path("code").asInt());
        long step = api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0).path("id").asLong();
        assertEquals(400, api("PUT", "/tasks/subtasks/" + step + "/complete", null).path("code").asInt());
        assertEquals(1, api("GET", "/check-ins?taskId=" + id, null).path("data").path("list").size());
        assertFalse(api("GET", "/dashboard/stats", null).path("data").has("moodQuote"));
    }

    @Test void completedLearningStillAcceptsAttendanceInsidePeriodAndRejectsDuplicates() throws Exception {
        long id = create(today.minusDays(5), today.plusDays(7));
        long step = api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0).path("id").asLong();
        assertEquals(200, api("PUT", "/tasks/subtasks/" + step + "/complete", null).path("code").asInt());
        assertEquals("IN_PROGRESS", api("GET", "/tasks/" + id, null).path("data").path("status").asText());
        for (int i=0;i<5;i++) assertEquals(200, api("POST", "/check-ins", Map.of("taskId",id,"checkInDate",today.minusDays(i).toString())).path("code").asInt());
        assertEquals("COMPLETED", api("GET", "/tasks/" + id, null).path("data").path("status").asText());
        assertEquals(400, api("POST", "/check-ins", Map.of("taskId",id,"checkInDate",today.minusDays(5).toString())).path("code").asInt());
        assertEquals(400, api("POST", "/actions/sync", Map.of("taskId",id,"rawInput","继续学习数据库", "durationMinutes",30,"requestId",UUID.randomUUID().toString())).path("code").asInt());
        var stats = api("GET", "/check-ins/tasks/"+id+"/stats", null).path("data");
        assertEquals(5, stats.path("checkedInDays").asInt()); assertEquals(100, stats.path("completionRate").asInt());
        assertEquals(100, api("GET", "/tasks/"+id,null).path("data").path("totalProgress").asInt());
        assertEquals(400, api("POST", "/check-ins", Map.of("taskId",id,"checkInDate",today.plusDays(1).toString())).path("code").asInt());
        database.update("update tasks set end_date=? where id=?", today.minusDays(1), id);
        assertEquals(400, api("POST", "/check-ins", Map.of("taskId",id)).path("code").asInt());
    }

    @Test void databasePaginationIsStableFilteredBoundedAndDoesNotChangeStatistics() throws Exception {
        long id=create(today.minusDays(50),today.plusDays(7));
        for(int i=0;i<41;i++) {
            database.update("insert into check_in_records (user_id,task_id,check_in_date,content,duration_minutes,automatic,created_at) values (?,?,?,?,0,false,now())",user.getId(),id,today.minusDays(i),"");
            database.update("insert into deferred_learning_records (user_id,task_id,content,duration_minutes,recorded_at,status) values (?,?,?,30,?,?)",user.getId(),id,"分页记录"+i,today.atTime(12,0),i<5?"APPLIED":"PENDING");
        }
        Set<Long> attendanceIds=new HashSet<>();
        for(int page=0;page<3;page++) {
            var data=api("GET","/check-ins?taskId="+id+"&page="+page+"&size=20",null).path("data");
            assertEquals(41,data.path("total").asInt()); assertEquals(page<2?20:1,data.path("list").size());
            for(var row:data.path("list")) assertTrue(attendanceIds.add(row.path("id").asLong()),"相同排序字段也不能跨页重复");
        }
        assertEquals(41,attendanceIds.size());
        assertEquals(41,api("GET","/check-ins/tasks/"+id+"/stats",null).path("data").path("checkedInDays").asInt());
        var filtered=api("GET","/check-ins?taskId="+id+"&startDate="+today+"&endDate="+today,null).path("data");
        assertEquals(1,filtered.path("total").asInt());
        Set<Long> pendingIds=new HashSet<>();
        for(int page=0;page<2;page++) {
            var data=api("GET","/actions/deferred?taskId="+id+"&page="+page+"&size=20",null).path("data");
            assertEquals(36,data.path("total").asInt()); assertEquals(page==0?20:16,data.path("list").size());
            for(var row:data.path("list")) { assertTrue(pendingIds.add(row.path("id").asLong())); assertNotEquals("APPLIED",row.path("status").asText()); }
        }
        for(String path:List.of("/check-ins?page=-1","/check-ins?size=101","/actions/deferred?page=-1","/actions/deferred?size=0"))
            assertEquals(400,api("GET",path,null).path("code").asInt());
        long second=create(today,today.plusDays(7));
        assertEquals(0,api("GET","/actions/deferred?taskId="+second,null).path("data").path("total").asInt());
    }

    @Test void retryAfterLostResponseKeepsOnePendingSubmissionAndRejectsKeyReuse() throws Exception {
        long id=create(today.minusDays(2),today.plusDays(7)); String key=UUID.randomUUID().toString();
        var body=Map.of("taskId",id,"content","断网重发的学习记录","durationMinutes",30,"requestId",key);
        var first=api("POST","/actions/deferred",body).path("data");
        var second=api("POST","/actions/deferred",body).path("data");
        assertEquals(first.path("id").asLong(),second.path("id").asLong());
        assertEquals(1,database.queryForObject("select count(*) from deferred_learning_records where task_id=?",Integer.class,id));
        assertEquals(0,database.queryForObject("select count(*) from check_in_records where task_id=?",Integer.class,id));
        assertEquals(409,api("POST","/actions/deferred",Map.of("taskId",id,"content","被篡改的正文","durationMinutes",30,"requestId",key)).path("code").asInt());
    }

    @Test void longTitleAndNonIntegerDurationAreClearBadRequestsWithoutWrites() throws Exception {
        var invalid = api("POST", "/tasks/manual", Map.of("title", "测".repeat(101), "category", "学习",
                "startDate", today.toString(), "endDate", today.plusDays(7).toString(), "targetDays", 5,
                "subTasks", List.of(Map.of("content", "学习数据库", "weight", 1))));
        assertEquals(400, invalid.path("code").asInt());
        assertTrue(invalid.path("message").asText().contains("100"));
        assertEquals(0, database.queryForObject("select count(*) from tasks where user_id=?", Integer.class, user.getId()));
        long id = create(today, today.plusDays(7));
        for (Object duration : List.of(-1, 0, 1441, 1.5, "半小时")) {
            assertEquals(400, api("POST", "/actions/sync", Map.of("taskId", id, "rawInput", "学习数据库", "durationMinutes", duration)).path("code").asInt());
        }
        assertEquals(400, api("POST", "/actions/sync", Map.of("taskId", id, "rawInput", "学习数据库", "durationInput", "30分钟")).path("code").asInt());
        assertEquals(0, database.queryForObject("select count(*) from deferred_learning_records where task_id=?", Integer.class, id));
    }

    @Test void foreignUserCannotReadEditDeleteCheckInOrRevokeAndDatabaseRemainsUnchanged() throws Exception {
        long id=create(today.minusDays(2),today.plusDays(7));
        long step=api("GET","/tasks/"+id+"/subtasks",null).path("data").get(0).path("id").asLong();
        var entry=logs.saveAndFlush(ActionLog.builder().userId(user.getId()).taskId(id).subTaskId(step).category(Task.Category.学习)
                .rawInput("权限隔离测试记录").contribution(BigDecimal.ZERO).durationMinutes(1).build());
        var ownPending=api("POST","/actions/deferred",Map.of("taskId",id,"content","权限隔离待分析记录","durationMinutes",30,"requestId",UUID.randomUUID().toString())).path("data").path("id").asLong();
        User foreign=users.saveAndFlush(User.builder().username("foreign-test-"+UUID.randomUUID()).password("TEST_ONLY").build());
        String ownerToken=token;
        try {
            token=jwt.generateToken(foreign.getId(),foreign.getUsername());
            assertEquals(0,api("GET","/tasks",null).path("data").size());
            assertEquals(403,api("GET","/tasks/"+id,null).path("code").asInt());
            assertEquals(403,api("PUT","/tasks/"+id,Map.of("title","非法修改","version",0)).path("code").asInt());
            assertEquals(403,api("DELETE","/tasks/"+id,null).path("code").asInt());
            assertEquals(403,api("POST","/check-ins",Map.of("taskId",id)).path("code").asInt());
            assertEquals(403,api("GET","/check-ins?taskId="+id,null).path("code").asInt());
            assertEquals(403,api("GET","/actions/deferred?taskId="+id,null).path("code").asInt());
            assertEquals(403,api("GET","/actions/history?taskId="+id,null).path("code").asInt());
            assertEquals(403,api("GET","/tasks/"+id+"/period-preview?startDate="+today+"&endDate="+today.plusDays(5),null).path("code").asInt());
            assertEquals(403,api("POST","/actions/deferred/"+ownPending+"/retry",Map.of()).path("code").asInt());
            assertEquals(403,api("PUT","/actions/deferred/"+ownPending,Map.of("content","越权编辑","durationMinutes",30)).path("code").asInt());
            assertEquals(403,api("DELETE","/actions/deferred/"+ownPending,null).path("code").asInt());
            assertEquals(403,api("POST","/actions/deferred",Map.of("taskId",id,"content","越权提交","durationMinutes",30,"requestId",UUID.randomUUID().toString())).path("code").asInt());
            assertEquals(403,api("DELETE","/actions/"+entry.getId(),null).path("code").asInt());
            assertEquals(403,api("PUT","/tasks/subtasks/"+step+"/complete",null).path("code").asInt());
            assertEquals(1,database.queryForObject("select count(*) from tasks where id=? and user_id=?",Integer.class,id,user.getId()));
            assertEquals(1,database.queryForObject("select count(*) from action_logs where id=?",Integer.class,entry.getId()));
            assertEquals(0,database.queryForObject("select count(*) from check_in_records where task_id=?",Integer.class,id));
        } finally { token=ownerToken;users.deleteById(foreign.getId()); }
    }

    @Test void invalidRequestsDoNotPersistAnythingAndDeleteActuallyRemovesAllRelations() throws Exception {
        assertEquals(400,api("POST","/tasks/manual",Map.of("title"," ","category","学习","startDate",today.toString(),"endDate",today.minusDays(1).toString(),"targetDays",1,"subTasks",List.of())).path("code").asInt());
        assertEquals(0,database.queryForObject("select count(*) from tasks where user_id=?",Integer.class,user.getId()));
        long id=create(today.minusDays(2),today.plusDays(7));
        assertEquals(400,api("POST","/check-ins",Map.of("taskId",id,"durationMinutes",-1)).path("code").asInt());
        assertEquals(200,api("POST","/check-ins",Map.of("taskId",id)).path("code").asInt());
        assertEquals(409,api("POST","/check-ins",Map.of("taskId",id)).path("code").asInt());
        assertEquals(1,database.queryForObject("select count(*) from check_in_records where task_id=?",Integer.class,id));
        assertEquals(200,api("DELETE","/tasks/"+id,null).path("code").asInt()); taskIds.remove(id);
        for(String table:List.of("tasks","sub_tasks","action_logs","check_in_records","deferred_learning_records"))
            assertEquals(0,database.queryForObject("select count(*) from "+table+" where "+(table.equals("tasks")?"id":"task_id")+"=?",Integer.class,id));
        assertEquals(404,api("GET","/tasks/"+id,null).path("code").asInt());
    }

    @Test void dashboardUsesPersistedWeightedGainEvenAfterStepWeightsChange() throws Exception {
        long id = create(today.minusDays(1), today.plusDays(7));
        long stepId = api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0).path("id").asLong();
        logs.saveAndFlush(ActionLog.builder().userId(user.getId()).taskId(id).subTaskId(stepId)
                .category(Task.Category.学习).rawInput("加权历史统计测试").durationMinutes(30)
                .contribution(new BigDecimal("0.20")).planContribution(new BigDecimal("0.05"))
                .createdAt(today.atTime(10, 0)).build());
        assertEquals(5.0, api("GET", "/dashboard/stats", null).path("data").path("todayTotalProgress").asDouble());
        database.update("update sub_tasks set weight=0.8 where id=?", stepId);
        assertEquals(5.0, api("GET", "/dashboard/stats", null).path("data").path("todayTotalProgress").asDouble());
    }

    @Test void pendingEditsAndRemovalPreserveDateRejectOldRequestsAndRespectClosedPlans() throws Exception {
        long id = create(today.minusDays(2), today.plusDays(7));
        String requestId = UUID.randomUUID().toString();
        Map<String,Object> submission = Map.of("taskId", id, "content", "尚未分析的旧描述", "durationMinutes", 30, "requestId", requestId);
        var original = api("POST", "/actions/deferred", submission).path("data");
        long old = original.path("id").asLong();
        var changed = api("PUT", "/actions/deferred/" + old, Map.of("content", "修正后的具体学习描述", "durationMinutes", 45)).path("data");
        long replacement = changed.path("id").asLong();
        assertNotEquals(old, replacement);
        assertEquals(original.path("recordedAt"), changed.path("recordedAt"));
        assertEquals(45, changed.path("durationMinutes").asInt());
        assertEquals(409, api("POST", "/actions/deferred", submission).path("code").asInt());
        assertEquals(409, api("POST", "/actions/deferred/"+old+"/retry", Map.of()).path("code").asInt());
        assertEquals(409, api("PUT", "/actions/deferred/"+old, Map.of("content", "旧表单", "durationMinutes", 1)).path("code").asInt());
        var list = api("GET", "/actions/deferred?taskId="+id, null).path("data");
        assertEquals(1, list.path("total").asInt());
        assertTrue(list.path("list").get(0).path("canRetry").asBoolean());
        var detail = api("GET", "/tasks/"+id, null).path("data");
        assertEquals(200, api("PUT", "/tasks/"+id, Map.of("version", detail.path("version").asLong(), "status", "ENDED")).path("code").asInt());
        var closed = api("GET", "/actions/deferred?taskId="+id, null).path("data").path("list").get(0);
        assertFalse(closed.path("canRetry").asBoolean());
        assertFalse(closed.path("retryBlockedReason").asText().isBlank());
        assertEquals(400, api("POST", "/actions/deferred/"+replacement+"/retry", Map.of()).path("code").asInt());
        assertEquals(400, api("PUT", "/actions/deferred/"+replacement, Map.of("content", "不应修改", "durationMinutes", 10)).path("code").asInt());
        assertEquals(200, api("DELETE", "/actions/deferred/"+replacement, null).path("code").asInt());
        assertEquals(0, api("GET", "/actions/deferred?taskId="+id, null).path("data").path("total").asInt());
        assertEquals("[已移除]", database.queryForObject("select content from deferred_learning_records where id=?", String.class, replacement));
        assertEquals(0, database.queryForObject("select count(*) from action_logs where task_id=?", Integer.class, id));
        assertEquals(0, database.queryForObject("select count(*) from check_in_records where task_id=?", Integer.class, id));
    }

    @Test void aiPreviewRejectsInvalidOrMissingPeriodBeforeAnyModelRequest() throws Exception {
        assertEquals(400, api("POST", "/tasks/deconstruct/preview", Map.of("title", "学习计算机网络",
                "startDate", "2026-12-30", "endDate", "2026-11-28", "targetDays", 20)).path("code").asInt());
        assertEquals(400, api("POST", "/tasks/deconstruct/preview", Map.of("title", "学习计算机网络",
                "startDate", today.toString(), "endDate", today.toString(), "targetDays", 2)).path("code").asInt());
        assertEquals(400, api("POST", "/tasks/deconstruct/preview", Map.of("title", "学习计算机网络")).path("code").asInt());
        assertEquals(0, database.queryForObject("select count(*) from tasks where user_id=?", Integer.class, user.getId()));
    }

    @Test void frontendApiModuleWorksAgainstRealHttpServerAndMysql() throws Exception {
        var process=new ProcessBuilder("node","../frontend/scripts/verify-http-contract.mjs");
        process.environment().put("TEST_API_BASE","http://localhost:"+port+"/api/v1");
        process.environment().put("TEST_API_TOKEN",token);
        process.redirectErrorStream(true);
        Process child=process.start();
        try {
            assertTrue(child.waitFor(60,java.util.concurrent.TimeUnit.SECONDS),"前端真实HTTP契约执行超时");
            String output=new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0,child.exitValue(),output);
            assertTrue(output.contains("\"passed\":true"),output);
            assertEquals(0,database.queryForObject("select count(*) from tasks where user_id=?",Integer.class,user.getId()));
            assertEquals(0,database.queryForObject("select count(*) from check_in_records where user_id=?",Integer.class,user.getId()));
        } finally { if(child.isAlive()) child.destroyForcibly(); }
    }

    @Test void historyPaginatesBeyondTwentyWithStableTieOrderAndDeletionRefillsPage() throws Exception {
        long id = create(today.minusDays(2), today.plusDays(7));
        long stepId = api("GET", "/tasks/" + id + "/subtasks", null).path("data").get(0).path("id").asLong();
        List<ActionLog> fixtures = new ArrayList<>();
        for (int index = 0; index < 21; index++) fixtures.add(ActionLog.builder().userId(user.getId()).taskId(id).subTaskId(stepId)
                .rawInput("分页集成测试记录-" + index).category(Task.Category.学习).durationMinutes(1)
                .contribution(BigDecimal.ZERO).createdAt(today.atTime(10, 0)).build());
        List<ActionLog> saved = logs.saveAllAndFlush(fixtures);
        var first = api("GET", "/actions/history?page=0&size=20", null).path("data");
        var second = api("GET", "/actions/history?page=1&size=20", null).path("data");
        assertEquals(21, first.path("total").asInt()); assertEquals(20, first.path("list").size());
        assertEquals(1, second.path("list").size());
        assertEquals(saved.get(20).getId().longValue(), first.path("list").get(0).path("id").asLong(), "同一秒按ID倒序，不能随机跳页");
        assertEquals(saved.get(0).getId().longValue(), second.path("list").get(0).path("id").asLong());
        var repeated = api("GET", "/actions/history?page=0&size=20", null).path("data").path("list");
        assertEquals(first.path("list"), repeated);
        assertEquals(200, api("DELETE", "/actions/" + saved.get(20).getId(), null).path("code").asInt());
        var refreshed = api("GET", "/actions/history?page=0&size=20", null).path("data");
        assertEquals(20, refreshed.path("total").asInt()); assertEquals(20, refreshed.path("list").size());
        assertEquals(saved.get(0).getId().longValue(), refreshed.path("list").get(19).path("id").asLong());
        assertEquals(0, api("GET", "/actions/history?page=1&size=20", null).path("data").path("list").size());
    }

    @Test void cancelledHistoryRemainsQueryableButAllDashboardMetricsExcludeIt() throws Exception {
        long completed = create(today.minusDays(2), today.plusDays(7));
        long active = create(today.minusDays(2), today.plusDays(7));
        long cancelled = create(today.minusDays(2), today.plusDays(7));
        database.update("update tasks set target_days=1 where id=?", completed);
        for (long id : List.of(completed, active)) {
            var response = api("POST", "/check-ins", Map.of("taskId", id));
            assertEquals(200, response.path("code").asInt());
            assertEquals("", response.path("data").path("content").asText());
            assertEquals(0, response.path("data").path("durationMinutes").asInt());
        }
        assertEquals(409, api("POST", "/check-ins", Map.of("taskId", active)).path("code").asInt());
        long completedStep = api("GET", "/tasks/" + completed + "/subtasks", null).path("data").get(0).path("id").asLong();
        assertEquals(200, api("PUT", "/tasks/subtasks/" + completedStep + "/complete", null).path("code").asInt());
        assertEquals(200, api("POST", "/check-ins", Map.of("taskId", cancelled, "checkInDate", today.minusDays(1).toString(),
                "content", "取消前打卡", "durationMinutes", 15)).path("code").asInt());
        logs.saveAndFlush(ActionLog.builder().userId(user.getId()).taskId(cancelled).rawInput("取消前学习记录")
                .category(Task.Category.学习).durationMinutes(77).contribution(new BigDecimal("0.25"))
                .createdAt(today.atTime(10, 0)).build());
        var before = api("GET", "/dashboard/stats", null).path("data");
        assertEquals(2, before.path("checkedInDays").asInt(), "同日两个计划只增加一个自然日");
        assertEquals(77, before.path("timeDistribution").get(0).path("minutes").asInt());
        assertEquals(200, api("PUT", "/tasks/" + cancelled, Map.of("status", "CANCELLED", "version", 0)).path("code").asInt());
        var after = api("GET", "/dashboard/stats", null).path("data");
        assertEquals(50, after.path("learningPlanCompletionRate").asInt());
        assertEquals(2, after.path("totalPlanCount").asInt()); assertEquals(1, after.path("cancelledPlanCount").asInt());
        assertEquals(1, after.path("checkedInDays").asInt()); assertEquals(1, after.path("currentCheckInStreak").asInt());
        assertEquals(1, after.path("longestCheckInStreak").asInt());
        assertEquals(0, after.path("todayTotalProgress").asInt()); assertEquals(0, after.path("timeDistribution").size());
        assertEquals(0, after.path("checkInTrend").get(5).path("checkInCount").asInt());
        assertEquals(1, after.path("checkInTrend").get(6).path("checkInCount").asInt());
        assertEquals(0, after.path("weeklyTrend").get(6).path("progress").asInt());
        assertFalse(after.has("checkInCompletionRate"));
        assertEquals(1, api("GET", "/check-ins?taskId=" + cancelled, null).path("data").path("list").size());
        assertEquals(1, api("GET", "/actions/history", null).path("data").path("total").asInt());
    }

    @Test void invalidCategoryAndDashboardAccessHaveConsistentClientErrorStatuses() throws Exception {
        var invalid = api("POST", "/tasks/manual", Map.of("title", "非法分类测试", "category", "INVALID",
                "startDate", today.toString(), "endDate", today.plusDays(7).toString(), "targetDays", 5,
                "subTasks", List.of(Map.of("content", "合法步骤", "weight", 1))));
        assertEquals(400, invalid.path("code").asInt());
        assertEquals("请选择有效的计划分类", invalid.path("message").asText());
        assertEquals(404, api("GET", "/dashboard/tasks/9223372036854775807/contributions", null).path("code").asInt());
    }
}
