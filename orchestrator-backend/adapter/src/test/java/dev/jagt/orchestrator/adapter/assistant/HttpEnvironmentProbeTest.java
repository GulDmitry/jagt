package dev.jagt.orchestrator.adapter.assistant;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class HttpEnvironmentProbeTest {

    private final HttpEnvironmentProbe probe = new HttpEnvironmentProbe();

    private static HttpServer answering(int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void answersWhereTheEnvironmentRespondsWithAPageThatNeedsALogin() throws IOException {
        HttpServer server = answering(401);
        try {
            assertThat(probe.answers("http://127.0.0.1:" + server.getAddress().getPort() + "/")).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotAnswerWhereTheEnvironmentRespondsWithAServerFault() throws IOException {
        HttpServer server = answering(503);
        try {
            assertThat(probe.answers("http://127.0.0.1:" + server.getAddress().getPort() + "/")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotAnswerWhereNothingListens() throws IOException {
        HttpServer server = answering(200);
        int port = server.getAddress().getPort();
        server.stop(0);

        assertThat(probe.answers("http://127.0.0.1:" + port + "/")).isFalse();
    }

    @Test
    void doesNotAnswerWhereTheEnvironmentRespondsWithTheFirstServerFaultStatus() throws IOException {
        HttpServer server = answering(500);
        try {
            assertThat(probe.answers("http://127.0.0.1:" + server.getAddress().getPort() + "/")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotAnswerAndKeepsItsInterruptWhereTheProbingThreadWasInterrupted() throws IOException {
        HttpServer server = answering(200);
        try {
            Thread.currentThread().interrupt();

            boolean answered = probe.answers("http://127.0.0.1:" + server.getAddress().getPort() + "/");

            assertThat(answered).isFalse();
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            server.stop(0);
        }
    }
}
