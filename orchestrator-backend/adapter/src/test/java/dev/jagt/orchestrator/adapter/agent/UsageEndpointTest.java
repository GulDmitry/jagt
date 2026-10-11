package dev.jagt.orchestrator.adapter.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock("loopback-ports")
@Execution(ExecutionMode.SAME_THREAD)
class UsageEndpointTest {

    @Test
    void holdsTheNextCallBackForAsLongAsA429AskedWithoutSendingIt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/usage", exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().add("Retry-After", "120");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        try {
            var endpoint = new UsageEndpoint(HttpClient.newHttpClient(),
                    Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/usage");
            endpoint.get(uri, Map.of());

            var second = endpoint.get(uri, Map.of());

            assertThat(second).isEqualTo(new UsageEndpoint.Refused(UsageEndpoint.NOT_SENT,
                    "rate limited until 2026-10-10T12:02:00Z"));
            assertThat(calls).hasValue(1);
        } finally {
            server.stop(0);
        }
    }
}
