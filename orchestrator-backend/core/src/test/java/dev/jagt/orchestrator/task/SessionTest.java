package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SessionTest {

  @Test
  void countsAStartedOverSessionAsTheNextGeneration() {
    Session next = Session.startedOver(Session.defaults().withGeneration(2));

    assertThat(next.generation()).isEqualTo(3);
  }

  @Test
  void isAliveOnlyWhereTheLastWindowReadShowedItsProcessRunning() {
    Session running =
        Session.defaults().withWindow(new Session.Window(Session.Window.State.RUNNING, 1, 0));
    Session dead =
        Session.defaults().withWindow(new Session.Window(Session.Window.State.DEAD_SHELL, 1, 0));

    assertThat(running.alive()).isTrue();
    assertThat(dead.alive()).isFalse();
    assertThat(Session.defaults().alive()).isNull();
  }

  @Test
  void readsTheFlatShapeAStateFileHolds() {
    String flat =
        """
        {"movedAt":5,"workStartedAt":4,"reportedAt":3,"halt":{"kind":"FAILED","at":6,"cause":"overloaded"},
         "quietSince":2,"window":{"state":"RUNNING","at":7,"printedAt":8},"log":"/logs/s.jsonl",
         "stampedAsk":true,"generation":3}\
        """;

    Session read = new JsonMapper().readValue(flat, Session.class);

    assertThat(read)
        .isEqualTo(
            new Session(
                5,
                4,
                3,
                new Session.Halt(Session.Halt.Kind.FAILED, 6, "overloaded"),
                2,
                "/logs/s.jsonl",
                true,
                new Session.Start(3, new Session.Window(Session.Window.State.RUNNING, 7, 8))));
  }

  @Test
  void writesTheFlatShapeAStateFileHolds() {
    Session session =
        Session.defaults()
            .withGeneration(3)
            .withWindow(new Session.Window(Session.Window.State.RUNNING, 7, 8));

    String written = new JsonMapper().writeValueAsString(session);

    assertThat(written)
        .contains("\"generation\":3")
        .contains("\"window\":{")
        .doesNotContain("\"start\"");
  }
}
