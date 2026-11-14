package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionLedgerTest {

  @TempDir Path root;

  private StateService state;

  @BeforeEach
  void putTask() {
    state = stateService().withRoot(root).build();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
  }

  @Test
  void writesTheSignOnTheTask() {
    Clock clock = Clock.fixed(Instant.ofEpochMilli(7_000), ZoneOffset.UTC);

    new SessionLedger(state, clock).record("ABC-1", null, new SessionSign.WorkStarted());

    assertThat(state.task("ABC-1").orElseThrow().session().workStartedAt()).isEqualTo(7_000);
  }

  @Test
  void recordsTheLogFileTheSessionAppendsToOnTheTask() {

    new SessionLedger(state, Clock.systemUTC())
        .record("ABC-1", Path.of("/logs/session.jsonl"), new SessionSign.WorkStarted());

    assertThat(state.task("ABC-1").orElseThrow().session().log()).isEqualTo("/logs/session.jsonl");
  }

  @Test
  void raisesPermissionOnTheTaskWhenPromptBlocksTheSession() {

    new SessionLedger(state, Clock.systemUTC())
        .sign(
            "ABC-1",
            new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK));

    assertThat(state.task("ABC-1").orElseThrow().report()).isEqualTo(AgentReport.PERMISSION);
  }

  @Test
  void stampsTheAskWithTheMomentItArrivedAndKeepsTheFirstStamp() {
    SessionSign.Asked ask =
        new SessionSign.Asked("waiting on the trust dialog", SessionSign.Asked.Channel.SCREEN);

    new SessionLedger(state, Clock.fixed(Instant.ofEpochMilli(7_000), ZoneOffset.UTC))
        .sign("ABC-1", ask);
    new SessionLedger(state, Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC))
        .sign("ABC-1", ask);

    assertThat(state.task("ABC-1").orElseThrow().session().askedAt()).isEqualTo(7_000);
  }
}
