package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoundRelayTest {

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000_000), ZoneOffset.UTC);

  @Test
  void relaysNoRoundIdenticalToTheLastOneWhenAnotherInstructionOverwroteTheContextFileBetween(
      @TempDir Path root) throws Exception {
    RoundRelayFixture fixture = new RoundRelayFixture(root, sessions, clock, "cases.md.dist");
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    ReviewFacts round =
        RoundRelayFixture.facts("running", "", List.of("https://host/mr/1#note_1\nbot: rename x"));
    fixture.relay.relay("ABC-42", "https://host/mr/1", round, "running", "");

    SweepResult again = fixture.relay.relay("ABC-42", "https://host/mr/1", round, "running", "");

    assertThat(again.kind()).isEqualTo(SweepResult.Kind.UNCHANGED);
    verify(sessions, times(1)).relayFresh(eq("ABC-42"), anyString());
  }

  @Test
  void relaysNothingWhileTheAgentsQuestionStandsAndTheChecksAreRed(@TempDir Path root)
      throws Exception {
    RoundRelayFixture fixture = new RoundRelayFixture(root, sessions, clock, "cases.md.dist");
    fixture.doors.state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.QUESTION)
            .build());

    SweepResult result =
        fixture.relay.relay(
            "ABC-42",
            "https://host/mr/1",
            RoundRelayFixture.facts("failed", "unit-tests\nCartTest failed", List.of()),
            "failed",
            "");

    assertThat(result.kind()).isEqualTo(SweepResult.Kind.PENDING);
    verify(sessions, never()).relayFresh(anyString(), anyString());
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
  }

  @Test
  void relaysNothingIntoTaskJagtStopped(@TempDir Path root) throws Exception {
    RoundRelayFixture fixture = new RoundRelayFixture(root, sessions, clock, "cases.md.dist");
    fixture.doors.state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .stop(new TaskStop(TaskStop.Trigger.SHIPS, 1L, "5 ships"))
            .build());

    SweepResult result =
        fixture.relay.relay(
            "ABC-42",
            "https://host/mr/1",
            RoundRelayFixture.facts("running", "", List.of("https://host/mr/1#note_9\nlee: why?")),
            "running",
            "");

    assertThat(result.kind()).isEqualTo(SweepResult.Kind.STOPPED);
    verify(sessions, never()).relayFresh(anyString(), anyString());
  }

  @Test
  void relaysTheThreadsAsDraftsAndCountsThem(@TempDir Path root) throws Exception {
    RoundRelayFixture fixture = new RoundRelayFixture(root, sessions, clock, "brief.md.dist");

    SweepResult result =
        fixture
            .over(RoundRelayFixture.relayingStops())
            .relay(
                "ABC-42",
                "http://mr/api, http://mr/web",
                RoundRelayFixture.facts(
                    "success",
                    "",
                    List.of("[api] http://mr/api#note_1", "[web] http://mr/web#note_2")),
                "success",
                "");

    assertThat(result.message()).contains("2 thread(s) relayed");
    verify(sessions).relayFresh(eq("ABC-42"), contains("review_replies.md"));
  }

  @Test
  void relaysTheThreadsWithoutTheChecksWhileTheAgentsQuestionAboutThemStands(@TempDir Path root)
      throws Exception {
    RoundRelayFixture fixture = new RoundRelayFixture(root, sessions, clock, "brief.md.dist");
    TaskStops stops = RoundRelayFixture.relayingStops();
    when(stops.asking("ABC-42")).thenReturn(true);

    fixture
        .over(stops)
        .relay(
            "ABC-42",
            "http://mr/api, http://mr/web",
            RoundRelayFixture.facts(
                "failed",
                "unit-tests\nCartTest failed",
                List.of("[api] http://mr/api#note_1", "[web] http://mr/web#note_2")),
            "failed",
            "");

    verify(sessions)
        .relayFresh(
            eq("ABC-42"),
            argThat(brief -> brief.contains("review_replies.md") && !brief.contains("Checks:")));
  }

  @Test
  void reportsRoundUnchangedWhileTheSessionHoldsTheTurnItOpened(@TempDir Path root)
      throws Exception {
    RoundRelayFixture fixture = new RoundRelayFixture(root, sessions, clock, "cases.md.dist");
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    RoundRelay relay = fixture.over(RoundRelayFixture.relayingStops());
    ReviewFacts round =
        RoundRelayFixture.facts("success", "", List.of("reviewer (a.java:3): drop the cache"));
    relay.relay("ABC-42", "http://mr/1", round, "success", "");

    SweepResult again = relay.relay("ABC-42", "http://mr/1", round, "success", "");

    assertThat(again.kind()).isEqualTo(SweepResult.Kind.UNCHANGED);
    assertThat(again.message()).contains("unchanged since the last relay");
  }

  @Test
  void relaysNoRoundWhereNoCaseSendsItsStepsAtTheTasksStatus(@TempDir Path root) throws Exception {
    RoundRelayFixture fixture = new RoundRelayFixture(root, sessions, clock, "cases.md.dist");
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DONE).build());

    SweepResult result =
        fixture
            .over(mock(TaskStops.class))
            .relay(
                "ABC-42",
                "https://host/mr/1",
                RoundRelayFixture.facts(
                    "running", "", List.of("https://host/mr/1#note_9\nlee: why?")),
                "running",
                "");

    assertThat(result.kind()).isEqualTo(SweepResult.Kind.REFUSED);
    verify(sessions, never()).relayFresh(anyString(), anyString());
  }
}
