package com.lifetrack.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class RestConfigTest {
    @Test void decompositionCanFinishAfterOrdinaryAnalysisBudgetWithoutExtendingOtherEndpoints() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/ai/", exchange -> {
            try {
                Thread.sleep(1700);
                byte[] body = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new RestConfig().restTemplate(1, 5);
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals("ok", client.getForObject(base + "/ai/deconstruct", String.class));
            assertThrows(ResourceAccessException.class, () -> client.getForObject(base + "/ai/progress-judge", String.class));
            assertThrows(ResourceAccessException.class, () -> client.getForObject(base + "/ai/report", String.class));
        } finally { server.stop(0); executor.shutdownNow(); }
    }
}
