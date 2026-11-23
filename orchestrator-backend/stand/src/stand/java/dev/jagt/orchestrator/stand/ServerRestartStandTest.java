package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ServerRestartStandTest {

  @ParameterizedTest
  @CsvSource({
    "JVM,got: remember this",
    "UNIT,resumed: 1 lines",
    "UPGRADE,resumed: 1 lines",
  })
  void taskAndConversationAreBackWhenJagtRestarts(Restart how, String shown) throws Exception {
    try (ServerRun server = ServerRun.start("restart-" + how)) {
      server.board().create("my-project", "keep working");
      await()
          .atMost(WaitCeiling.LIMIT)
          .ignoreExceptions()
          .untilAsserted(() ->
              assertThat(server.board().text("/api/tasks/screen?task=keep-working"))
                  .contains("agent working"));
      server.board().say("keep-working", "remember this");
      await()
          .atMost(WaitCeiling.LIMIT)
          .untilAsserted(() ->
              assertThat(server.board().text("/api/tasks/screen?task=keep-working"))
                  .contains("got: remember this"));

      server.restart(how);
      server.board().press("keep-working", "focus");

      assertThat(server.board().state().path("tasks").has("keep-working")).isTrue();
      await()
          .atMost(WaitCeiling.LIMIT)
          .untilAsserted(() ->
              assertThat(server.board().text("/api/tasks/screen?task=keep-working"))
                  .contains(shown)
                  .contains("remember this"));
      server.passed();
    }
  }

  @Test
  void stateHoldsTheTaskWithItsSessionGoneWhenJagtGetsSigterm() throws Exception {
    try (ServerRun server = ServerRun.start("sigterm")) {
      server.board().create("my-project", "keep working");
      await()
          .atMost(WaitCeiling.LIMIT)
          .ignoreExceptions()
          .untilAsserted(() ->
              assertThat(server.board().text("/api/tasks/screen?task=keep-working"))
                  .contains("agent working"));

      server.engine().terminate();

      JsonNode state = JsonMapper.builder().build().readTree(server.engine().state());
      assertThat(state.at("/tasks/keep-working/session/halt/kind").asString()).isEqualTo("GONE");
      server.passed();
    }
  }
}
