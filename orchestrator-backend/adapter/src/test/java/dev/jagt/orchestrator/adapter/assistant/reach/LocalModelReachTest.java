package dev.jagt.orchestrator.adapter.assistant.reach;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.agent.opencode.OpenCodeProperties;
import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LocalModelReachTest {

  private @Nullable FakeModelServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void anEndpointServingAnotherModelIsLostReach() throws IOException {
    AtomicReference<String> body = new AtomicReference<>("{\"data\":[{\"id\":\"other\"}]}");
    server = new FakeModelServer(body);
    String endpoint = "http://127.0.0.1:" + server.port() + "/v1/";
    ReachLedger reach = new ReachLedger();

    new LocalModelReach(
            OpenCodeProperties.defaults(),
            new LocalModelProperties(endpoint, "qwen", Duration.ofSeconds(5), List.of()),
            reach)
        .run();

    assertThat(reach.standing())
        .extracting(ReachLost::said)
        .containsExactly(endpoint + " does not serve qwen");
  }

  @Test
  void theLostMarkEndsWhenTheModelIsListedAgain() throws IOException {
    AtomicReference<String> body = new AtomicReference<>("{\"data\":[{\"id\":\"other\"}]}");
    server = new FakeModelServer(body);
    String endpoint = "http://127.0.0.1:" + server.port() + "/v1";
    ReachLedger reach = new ReachLedger();
    LocalModelReach job =
        new LocalModelReach(
            new OpenCodeProperties("", endpoint, "qwen", Map.of()),
            LocalModelProperties.off(),
            reach);
    job.run();
    assertThat(reach.standing()).hasSize(1);

    body.set("{\"data\":[{\"id\":\"qwen\"}]}");
    job.run();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void anUnreachableAssistantEndpointIsLostReach() throws IOException {
    AtomicReference<String> body = new AtomicReference<>("{}");
    server = new FakeModelServer(body);
    String endpoint = "http://127.0.0.1:" + server.port() + "/v1";
    server.stop();
    ReachLedger reach = new ReachLedger();

    new LocalModelReach(
            OpenCodeProperties.defaults(),
            new LocalModelProperties(endpoint, "qwen", Duration.ofSeconds(5), List.of()),
            reach)
        .run();

    assertThat(reach.standing())
        .extracting(ReachLost::said)
        .containsExactly("assistant.local cannot reach " + endpoint);
  }
}
