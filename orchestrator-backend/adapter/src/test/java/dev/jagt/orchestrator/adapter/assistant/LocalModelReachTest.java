package dev.jagt.orchestrator.adapter.assistant;

import com.sun.net.httpserver.HttpServer;
import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.ReachLedger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LocalModelReachTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void anEndpointServingAnotherModelIsALostReach() throws IOException {
        AtomicReference<String> body = new AtomicReference<>("{\"data\":[{\"id\":\"other\"}]}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        ReachLedger reach = new ReachLedger();

        new LocalModelReach(OpenCodeProperties.defaults(),
                new LocalModelProperties(endpoint, "qwen", Duration.ofSeconds(5), List.of()), reach).run();

        assertThat(reach.standing()).extracting(ReachLost::said)
                .containsExactly(endpoint + " does not serve qwen");
    }

    @Test
    void theLostMarkEndsWhenTheModelIsListedAgain() throws IOException {
        AtomicReference<String> body = new AtomicReference<>("{\"data\":[{\"id\":\"other\"}]}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        ReachLedger reach = new ReachLedger();
        LocalModelReach job = new LocalModelReach(new OpenCodeProperties("", endpoint, "qwen", null),
                LocalModelProperties.off(), reach);
        job.run();
        assertThat(reach.standing()).hasSize(1);

        body.set("{\"data\":[{\"id\":\"qwen\"}]}");
        job.run();

        assertThat(reach.standing()).isEmpty();
    }

    @Test
    void anUnreachableAssistantEndpointIsALostReach() throws IOException {
        AtomicReference<String> body = new AtomicReference<>("{}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        server.stop(0);
        ReachLedger reach = new ReachLedger();

        new LocalModelReach(OpenCodeProperties.defaults(),
                new LocalModelProperties(endpoint, "qwen", Duration.ofSeconds(5), List.of()), reach).run();

        assertThat(reach.standing()).extracting(ReachLost::said)
                .containsExactly("assistant.local cannot reach " + endpoint);
    }
}
