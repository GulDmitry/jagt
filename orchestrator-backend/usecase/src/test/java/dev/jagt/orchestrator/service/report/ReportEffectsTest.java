package dev.jagt.orchestrator.service.report;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.presence.SessionWakes;
import dev.jagt.orchestrator.service.session.LinkRefusals;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Wake;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.task.turn.Turn;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportEffectsTest {

  private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
  private final TaskStops stops = mock(TaskStops.class);
  private final LinkRefusals links = mock(LinkRefusals.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @TempDir Path dir;

  private StateService state;
  private TaskStops realStops;
  private TaskTurns turns;
  private ReportEffects effects;
  private ReportEffects realEffects;

  @BeforeEach
  void wireEffects() {
    state = stateService().withRoot(dir).build();
    realStops = taskStops().withRoot(dir).build(state, clock);
    turns = new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn())));
    effects =
        new ReportEffects(
            new SessionWakes(state, turns, clock),
            turns,
            stops,
            InfraWaitProperties.defaults(),
            links);
    realEffects =
        new ReportEffects(
            new SessionWakes(state, turns, clock),
            turns,
            realStops,
            InfraWaitProperties.defaults(),
            links);
  }

  private final AgentStatusMessage waits =
      new AgentStatusMessage("IN_PROGRESS", "db down: ping db.internal", "wait", null, Map.of());

  @Test
  void armsWakeFiveMinutesOutForTheTurnTheWaitClosed() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withTurn(new Turn("context", 4, "", 0, Map.of(), List.of())));

    effects.after(waits, "ABC-42");

    Turn turn = state.task("ABC-42").orElseThrow().work().turnOrNone();
    assertThat(Wake.parse(turn.mark(Wake.MARK)))
        .contains(new Wake(4, NOW.plusSeconds(300).toEpochMilli(), Step.WAIT_OVER));
  }

  @Test
  void armsTheNextWaitWhenTheSessionAnswersWaitOverWithWaitAgain() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withTurn(new Turn("wait-over", 4, "", 0, Map.of("waits", "1"), List.of())));

    effects.after(waits, "ABC-42");

    Turn turn = state.task("ABC-42").orElseThrow().work().turnOrNone();
    assertThat(Wake.parse(turn.mark(Wake.MARK)).orElseThrow().at())
        .isEqualTo(NOW.plusSeconds(900).toEpochMilli());
    assertThat(turn.mark("waits")).isEqualTo("2");
  }

  @Test
  void stopsTheTaskWithTheSessionsWordsOnceEveryWaitWasSpent() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withTurn(new Turn("wait-over", 4, "", 0, Map.of("waits", "3"), List.of())));

    effects.after(waits, "ABC-42");

    verify(stops).stop("ABC-42", TaskStop.Trigger.WAIT, "db down: ping db.internal");
  }

  @Test
  void armsNothingForReportThatIsNoWait() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withTurn(new Turn("context", 4, "", 0, Map.of(), List.of())));

    effects.after(AgentStatusMessage.of("IN_PROGRESS", "working"), "ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().work().turnOrNone().mark(Wake.MARK)).isNull();
    verify(stops, never()).stop("ABC-42", TaskStop.Trigger.WAIT, "working");
  }

  @Test
  void refusesWaitWhereNoWaitIsConfigured() {
    ReportEffects off =
        new ReportEffects(
            new SessionWakes(state, turns, clock),
            turns,
            stops,
            new InfraWaitProperties(List.of()),
            links);

    MessageContext judged = off.judged(MessageContext.NONE);

    assertThat(waits.reportViolations(judged)).hasSize(1);
  }

  @Test
  void asksTheSessionAgainAtTheLastWaitOnceTheTaskStoppedAsWaiting() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withTurn(new Turn("wait-over", 4, "", 0, Map.of("waits", "3"), List.of())));
    realEffects.after(waits, "ABC-42");

    Turn turn = state.task("ABC-42").orElseThrow().work().turnOrNone();
    assertThat(Wake.parse(turn.mark(Wake.MARK)))
        .contains(new Wake(4, NOW.plusSeconds(2700).toEpochMilli(), Step.WAIT_OVER));
  }

  @Test
  void keepsTheWaitStopAndTheNextWakeDueWhenTheSessionWaitsAgain() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withTurn(new Turn("wait-over", 4, "", 0, Map.of("waits", "3"), List.of())));
    realStops.stop("ABC-42", TaskStop.Trigger.WAIT, "db down: ping db.internal");
    realEffects.after(waits, "ABC-42");

    Turn turn = state.task("ABC-42").orElseThrow().work().turnOrNone();
    assertThat(realStops.stopped("ABC-42")).isTrue();
    assertThat(Wake.parse(turn.mark(Wake.MARK)))
        .contains(new Wake(4, NOW.plusSeconds(2700).toEpochMilli(), Step.WAIT_OVER));
  }

  @Test
  void endsTheWaitStopWhenTheSessionReportsProgressWithoutWait() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withTurn(new Turn("wait-over", 4, "", 0, Map.of("waits", "3"), List.of())));
    realStops.stop("ABC-42", TaskStop.Trigger.WAIT, "db down: ping db.internal");
    realEffects.after(AgentStatusMessage.of("IN_PROGRESS", "db is back"), "ABC-42");

    assertThat(realStops.stopped("ABC-42")).isFalse();
  }
}
