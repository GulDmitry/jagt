package dev.jagt.orchestrator.surface.ui.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.mock.env.MockEnvironment;

class PortCheckTest {

  @Test
  void refusesTheStartWithOneLineNamingThePortWhenAnotherProcessListensOnIt() throws IOException {
    try (ServerSocket occupied = new ServerSocket()) {
      occupied.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      int port = occupied.getLocalPort();

      var refusal =
          new PortCheck(code -> {})
              .refusal(new MockEnvironment().withProperty("server.port", String.valueOf(port)));

      assertThat(refusal)
          .hasValueSatisfying(line ->
              assertThat(line)
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

    var refusal =
        new PortCheck(code -> {})
            .refusal(new MockEnvironment().withProperty("server.port", String.valueOf(freePort)));

    assertThat(refusal).isEmpty();
  }

  @Test
  void exitsWithCodeOneWhenAnotherProcessListensOnThePort() throws IOException {
    List<Integer> exits = new ArrayList<>();
    try (ServerSocket occupied = new ServerSocket()) {
      occupied.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      var environment =
          new MockEnvironment()
              .withProperty("server.port", String.valueOf(occupied.getLocalPort()));

      new PortCheck(exits::add)
          .onApplicationEvent(
              new ApplicationEnvironmentPreparedEvent(
                  new DefaultBootstrapContext(),
                  new SpringApplication(),
                  new String[0],
                  environment));
    }

    assertThat(exits).containsExactly(1);
  }

  @Test
  void runsBeforeTheLoggingIsConfigured() {
    assertThat(new PortCheck(code -> {}).getOrder())
        .isLessThan(LoggingApplicationListener.DEFAULT_ORDER);
  }
}
