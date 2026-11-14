package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestCaseDoorBuilder.caseDoor;
import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static dev.jagt.orchestrator.service.TestReviewAnswerBuilder.reviewAnswer;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoundRelayTest {

  @TempDir Path root;

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000_000), ZoneOffset.UTC);
  private final ReviewFacts noted =
      reviewAnswer()
          .withStatus("running")
          .withDiscussion(
              new ReviewFacts.Discussion(
                  List.of("https://host/mr/1#note_9\nlee: why?"), List.of(), List.of()))
          .build()
          .facts()
          .orElseThrow();
  private final TaskStops relayingStops = mock(TaskStops.class);
  private OrchestratorPaths paths;
  private StateService state;
  private TaskStops stops;
  private CaseDoor door;
  private SessionBrief brief;
  private StepRelay steps;

  @BeforeEach
  void setUp() throws IOException {
    paths = orchestratorPaths().withRoot(root).build();
    state = stateService().withRoot(root).build();
    stops = taskStops().withRoot(root).build(state, clock);
    door = caseDoor().withRoot(root).build(state, stops, clock);
    brief = new SessionBrief(paths);
    steps =
        new StepRelay(
            brief,
            sessions,
            door,
            new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn()))));
    Files.createDirectories(root.resolve("roles/session"));
    when(relayingStops.round(anyString(), any(), anyString(), anyString()))
        .thenReturn(TaskStops.Relay.RELAY);
    when(relayingStops.sinceLast(anyString(), any())).thenReturn("");
  }

  @Test
  void relaysNoRoundIdenticalToTheLastOneWhenAnotherInstructionOverwroteTheContextFileBetween()
      throws Exception {
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    RoundRelay relay = new RoundRelay(stops, steps, door, brief);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    ReviewFacts round =
        reviewAnswer()
            .withStatus("running")
            .withDiscussion(
                new ReviewFacts.Discussion(
                    List.of("https://host/mr/1#note_1\nbot: rename x"), List.of(), List.of()))
            .build()
            .facts()
            .orElseThrow();
    relay.relay("ABC-42", "https://host/mr/1", round, "running", "");

    SweepResult again = relay.relay("ABC-42", "https://host/mr/1", round, "running", "");

    assertThat(again.kind()).isEqualTo(SweepResult.Kind.UNCHANGED);
    verify(sessions, times(1)).relayFresh(eq("ABC-42"), anyString());
  }

  @Test
  void relaysNothingWhileTheAgentsQuestionStandsAndTheChecksAreRed() throws Exception {
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    RoundRelay relay = new RoundRelay(stops, steps, door, brief);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.QUESTION)
            .build());

    SweepResult result =
        relay.relay(
            "ABC-42",
            "https://host/mr/1",
            reviewAnswer()
                .withStatus("failed")
                .withFailure("unit-tests\nCartTest failed")
                .withDiscussion(new ReviewFacts.Discussion(List.of(), List.of(), List.of()))
                .build()
                .facts()
                .orElseThrow(),
            "failed",
            "");

    assertThat(result.kind()).isEqualTo(SweepResult.Kind.PENDING);
    verify(sessions, never()).relayFresh(anyString(), anyString());
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
  }

  @Test
  void relaysNothingIntoTaskJagtStopped() throws Exception {
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    RoundRelay relay = new RoundRelay(stops, steps, door, brief);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .stop(new TaskStop(TaskStop.Trigger.SHIPS, 1L, "5 ships"))
            .build());

    SweepResult result = relay.relay("ABC-42", "https://host/mr/1", noted, "running", "");

    assertThat(result.kind()).isEqualTo(SweepResult.Kind.STOPPED);
    verify(sessions, never()).relayFresh(anyString(), anyString());
  }

  @Test
  void relaysTheThreadsAsDraftsAndCountsThem() throws Exception {
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    RoundRelay relay = new RoundRelay(relayingStops, steps, door, brief);

    SweepResult result =
        relay.relay(
            "ABC-42",
            "http://mr/api, http://mr/web",
            reviewAnswer()
                .withStatus("success")
                .withDiscussion(
                    new ReviewFacts.Discussion(
                        List.of("[api] http://mr/api#note_1", "[web] http://mr/web#note_2"),
                        List.of(),
                        List.of()))
                .build()
                .facts()
                .orElseThrow(),
            "success",
            "");

    assertThat(result.message()).contains("2 thread(s) relayed");
    verify(sessions).relayFresh(eq("ABC-42"), contains("review_replies.md"));
  }

  @Test
  void relaysTheThreadsWithoutTheChecksWhileTheAgentsQuestionAboutThemStands() throws Exception {
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    RoundRelay relay = new RoundRelay(relayingStops, steps, door, brief);
    when(relayingStops.asking("ABC-42")).thenReturn(true);

    relay.relay(
        "ABC-42",
        "http://mr/api, http://mr/web",
        reviewAnswer()
            .withStatus("failed")
            .withFailure("unit-tests\nCartTest failed")
            .withDiscussion(
                new ReviewFacts.Discussion(
                    List.of("[api] http://mr/api#note_1", "[web] http://mr/web#note_2"),
                    List.of(),
                    List.of()))
            .build()
            .facts()
            .orElseThrow(),
        "failed",
        "");

    verify(sessions)
        .relayFresh(
            eq("ABC-42"),
            argThat(sent -> sent.contains("review_replies.md") && !sent.contains("Checks:")));
  }

  @Test
  void reportsRoundUnchangedWhileTheSessionHoldsTheTurnItOpened() throws Exception {
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    RoundRelay relay = new RoundRelay(relayingStops, steps, door, brief);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    ReviewFacts round =
        reviewAnswer()
            .withStatus("success")
            .withDiscussion(
                new ReviewFacts.Discussion(
                    List.of("reviewer (a.java:3): drop the cache"), List.of(), List.of()))
            .build()
            .facts()
            .orElseThrow();
    relay.relay("ABC-42", "http://mr/1", round, "success", "");

    SweepResult again = relay.relay("ABC-42", "http://mr/1", round, "success", "");

    assertThat(again.kind()).isEqualTo(SweepResult.Kind.UNCHANGED);
    assertThat(again.message()).contains("unchanged since the last relay");
  }

  @Test
  void relaysNoRoundWhereNoCaseSendsItsStepsAtTheTasksStatus() throws Exception {
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    RoundRelay relay = new RoundRelay(mock(TaskStops.class), steps, door, brief);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DONE).build());

    SweepResult result = relay.relay("ABC-42", "https://host/mr/1", noted, "running", "");

    assertThat(result.kind()).isEqualTo(SweepResult.Kind.REFUSED);
    verify(sessions, never()).relayFresh(anyString(), anyString());
  }
}
