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
        return mapper.readTree(response.body());
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
        assertEquals(1, history.path("data").size());
        assertEquals(30, history.path("data").get(0).path("durationMinutes").asInt());
        assertEquals(2, api("GET", "/check-ins/tasks/" + id + "/stats", null).path("data").path("checkedInDays").asInt());
        assertEquals(0, api("GET", "/actions/history", null).path("data").path("total").asInt(), "正式打卡不会伪造AI学习记录");
        var plans = api("GET", "/tasks?status=IN_PROGRESS&startDate=" + today + "&endDate=" + today, null).path("data");
        assertEquals(1, plans.size()); assertEquals(id, plans.get(0).path("id").asLong());
        var noMatch = api("GET", "/tasks?startDate=" + today.plusDays(30), null).path("data");
        assertEquals(0, noMatch.size());
        assertEquals("NOT_STARTED", api("GET", "/tasks/" + future, null).path("data").path("status").asText());
        assertEquals(400, api("GET", "/check-ins?taskId=" + id + "&startDate=" + today + "&endDate=" + today.minusDays(1), null).path("code").asInt());
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
}
