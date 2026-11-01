package dev.jagt.orchestrator.surface.ui;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

class PortGuardTest {

    @Test
    void refusesTheStartWithOneLineNamingThePortWhenAnotherProcessListensOnIt() throws IOException {
        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            int port = occupied.getLocalPort();

            var refusal = new PortGuard().refusal(new MockEnvironment()
                    .withProperty("server.port", String.valueOf(port)));

            assertThat(refusal).hasValueSatisfying(line -> assertThat(line)
                    .startsWith("start refused")
                    .contains("port=" + port)
                    .contains("another jagt or process holds it")
                    .doesNotContain("\n"));
        }
    }

    @Test
    void letsTheStartGoOnWhenTheConfiguredPortIsFree() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = probe.getLocalPort();
        }

        var refusal = new PortGuard().refusal(new MockEnvironment()
                .withProperty("server.port", String.valueOf(freePort)));

        assertThat(refusal).isEmpty();
    }

    @Test
    void runsBeforeTheLoggingIsConfigured() {
        assertThat(new PortGuard().getOrder()).isLessThan(LoggingApplicationListener.DEFAULT_ORDER);
    }
}
