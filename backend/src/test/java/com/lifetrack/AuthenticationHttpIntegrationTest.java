package com.lifetrack;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.dev-login-enabled=true", "auth.demo-phone-enabled=true", "ai.base-url=http://127.0.0.1:1/ai" })
@ActiveProfiles({"test", "auth-probe"})
@Import(AuthenticationHttpIntegrationTest.ErrorProbe.class)
class AuthenticationHttpIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper mapper;
    final HttpClient client = HttpClient.newHttpClient();
    JsonNode call(String method, String path, Object body, int expected) throws Exception {
        String json = body instanceof String raw ? raw : body == null ? "" : mapper.writeValueAsString(body);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json").timeout(Duration.ofSeconds(15))
                .method(method, HttpRequest.BodyPublishers.ofString(json)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), response.body());
        var result = mapper.readTree(response.body()); assertEquals(expected, result.path("code").asInt()); return result;
    }
    @Test void nonDevelopmentEnvironmentDeniesDemonstrationEvenWhenSwitchesAreTrue() throws Exception {
        var options = call("GET", "/auth/options", null, 200).path("data");
        assertFalse(options.path("devLoginEnabled").asBoolean()); assertFalse(options.path("demoPhoneEnabled").asBoolean());
        assertTrue(options.path("demoVerifyCode").isNull());
        call("POST", "/auth/login/dev", null, 403);
        call("POST", "/auth/login/phone", Map.of("phone","13800001234","verifyCode","123456"), 403);
    }
    @Test void malformedAuthenticationInputHasSafeClientErrors() throws Exception {
        for (String phone : new String[]{"1", "abc", "", "138000000000"}) {
            var result = call("POST", "/auth/login/phone", Map.of("phone", phone, "verifyCode", "123456"),400);
            assertFalse(result.path("message").asText().contains("substring"));
            assertFalse(result.path("message").asText().contains("Range"));
        }
        call("POST", "/auth/login/phone", Map.of(),400);
        call("POST", "/auth/login/phone", "{",400);
        call("POST", "/auth/login/phone", Map.of("phone","13800001234","verifyCode","abc"),400);
    }
    @Test void removedAndProtectedRoutesUseActualHttpStatuses() throws Exception {
        call("POST", "/auth/login/wechat", Map.of("code","removed"),404);
        call("GET", "/tasks", null,401);
        call("GET", "/auth/login/dev", null,405);
    }
    @Test void unexpectedExceptionsNeverLeakInternalMessage() throws Exception {
        var result = call("GET", "/test-errors/runtime", null,500);
        assertEquals("服务器繁忙，请稍后再试", result.path("message").asText());
        assertFalse(result.toString().contains("internal-secret-detail"));
        call("GET", "/test-errors/argument", null,400);
    }
    @RestController @Profile("auth-probe")
    static class ErrorProbe {
        @GetMapping("/api/v1/test-errors/runtime") public Object runtime() { throw new RuntimeException("internal-secret-detail"); }
        @GetMapping("/api/v1/test-errors/argument") public Object argument() { throw new IllegalArgumentException(); }
    }
}
