package dev.jagt.orchestrator.adapter.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void returnsTheBodyOfASuccessfulCallAndSendsEveryHeader() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/usage", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"ok\":true}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var endpoint = new UsageEndpoint(HttpClient.newHttpClient(),
                    Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/usage");

            var reply = endpoint.get(uri, Map.of("Authorization", "Bearer tok-abc"));

            assertThat(reply).isEqualTo(new UsageEndpoint.Body("{\"ok\":true}"));
            assertThat(authorization).hasValue("Bearer tok-abc");
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "500, http 500",
            "403, http 403",
            "300, http 300",
    })
    void refusesAnyAnswerOutsideTheSuccessRangeNamingItsStatus(int status, String cause) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/usage", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            var endpoint = new UsageEndpoint(HttpClient.newHttpClient(),
                    Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/usage");

            var reply = endpoint.get(uri, Map.of());

            assertThat(reply).isEqualTo(new UsageEndpoint.Refused(status, cause));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void holdsBackFiveMinutesWhenA429GivesNoUsableRetryAfter() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/usage", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        try {
            var endpoint = new UsageEndpoint(HttpClient.newHttpClient(),
                    Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/usage");

            var first = endpoint.get(uri, Map.of());

            assertThat(first).isEqualTo(new UsageEndpoint.Refused(429, "rate limited until 2026-10-10T12:05:00Z"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refusesWithoutSendingWhenNothingListens() throws Exception {
        int closedPort;
        try (var socket = new java.net.ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        var endpoint = new UsageEndpoint(HttpClient.newHttpClient(),
                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));

        var reply = endpoint.get(URI.create("http://127.0.0.1:" + closedPort + "/usage"), Map.of());

        assertThat(reply).isInstanceOfSatisfying(UsageEndpoint.Refused.class,
                refused -> assertThat(refused.status()).isEqualTo(UsageEndpoint.NOT_SENT));
    }
}
