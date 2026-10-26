package com.lifetrack;

import com.fasterxml.jackson.databind.*;
import com.lifetrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.dev-login-enabled=true", "auth.demo-phone-enabled=true", "auth.demo-code=654321",
        "jwt.secret=test-only-development-login-secret-at-least-thirty-two-characters" })
@ActiveProfiles("dev")
class DevelopmentLoginIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    final HttpClient client = HttpClient.newHttpClient();
    JsonNode call(String method, String path, Object body, int expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type","application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        var response = client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(expected,response.statusCode(),response.body());
        var json = mapper.readTree(response.body()); assertEquals(expected,json.path("code").asInt()); return json;
    }
    @Test void configuredDemoLoginWorksAndEqualPhoneSuffixesDoNotCollide() throws Exception {
        var options = call("GET","/auth/options",null,200).path("data");
        assertTrue(options.path("devLoginEnabled").asBoolean()); assertTrue(options.path("demoPhoneEnabled").asBoolean());
        assertEquals("654321",options.path("demoVerifyCode").asText());
        String suffix;
        do { suffix = String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(1000,10000)) + "1234"; }
        while (users.findByPhone("199" + suffix).isPresent() || users.findByPhone("198" + suffix).isPresent());
        List<Long> ids = new ArrayList<>();
        try {
            var first = call("POST","/auth/login/phone",Map.of("phone","199" + suffix,"verifyCode","654321"),200).path("data");
            ids.add(first.path("userId").asLong()); assertFalse(first.path("token").asText().isBlank());
            var second = call("POST","/auth/login/phone",Map.of("phone","198" + suffix,"verifyCode","654321"),200).path("data");
            ids.add(second.path("userId").asLong()); assertNotEquals(first.path("userId"),second.path("userId"));
            assertNotEquals(first.path("username").asText(),second.path("username").asText());
            var repeated = call("POST","/auth/login/phone",Map.of("phone","199" + suffix,"verifyCode","654321"),200).path("data");
            assertEquals(first.path("userId"),repeated.path("userId"));
            call("POST","/auth/login/phone",Map.of("phone","199" + suffix,"verifyCode","111111"),400);
        } finally { ids.forEach(users::deleteById); }
    }
}
