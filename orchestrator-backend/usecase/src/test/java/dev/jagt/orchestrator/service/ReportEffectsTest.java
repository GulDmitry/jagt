package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import dev.jagt.orchestrator.task.Wake;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ReportEffectsTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
  private final TaskStops stops = mock(TaskStops.class);
  private final AgentStatusMessage waits =
      new AgentStatusMessage("IN_PROGRESS", "db down: ping db.internal", "wait", null, Map.of());

  private StateService state(Path dir) {
    return new StateService(
        new JsonMapper(),
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(dir.toString())
                .withStateFile(dir.resolve("state.json").toString())),
        reducer, event -> {});
  }

  private ReportEffects effects(StateService state, InfraWaitProperties properties) {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    TaskTurns turns =
        new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn())));
    return new ReportEffects(
        new SessionWakes(state, turns, clock), turns, stops, properties, mock(LinkRefusals.class));
  }

  private static TaskState taskAnswering(String step, @Nullable String waited) {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();
    Turn turn =
        new Turn(step, 4, "", 0, waited == null ? Map.of() : Map.of("waits", waited), List.of());
    return task.withTurn(turn);
  }

  @Test
  void armsWakeFiveMinutesOutForTheTurnTheWaitClosed(@TempDir Path dir) {
    StateService state = state(dir);
    state.putTask("ABC-42", taskAnswering("context", null));

    effects(state, InfraWaitProperties.defaults()).after(waits, "ABC-42");

    Turn turn = state.task("ABC-42").orElseThrow().turnOrNone();
    assertThat(Wake.parse(turn.mark(Wake.MARK)))
        .contains(new Wake(4, NOW.plusSeconds(300).toEpochMilli(), Step.WAIT_OVER));
  }

  @Test
  void armsTheNextWaitWhenTheSessionAnswersWaitOverWithWaitAgain(@TempDir Path dir) {
    StateService state = state(dir);
    state.putTask("ABC-42", taskAnswering("wait-over", "1"));

    effects(state, InfraWaitProperties.defaults()).after(waits, "ABC-42");

    Turn turn = state.task("ABC-42").orElseThrow().turnOrNone();
    assertThat(Wake.parse(turn.mark(Wake.MARK)).orElseThrow().at())
        .isEqualTo(NOW.plusSeconds(900).toEpochMilli());
    assertThat(turn.mark("waits")).isEqualTo("2");
  }

  @Test
  void stopsTheTaskWithTheSessionsWordsOnceEveryWaitWasSpent(@TempDir Path dir) {
    StateService state = state(dir);
    state.putTask("ABC-42", taskAnswering("wait-over", "3"));

    effects(state, InfraWaitProperties.defaults()).after(waits, "ABC-42");

    verify(stops).stop("ABC-42", TaskStop.Trigger.WAIT, "db down: ping db.internal");
  }

  @Test
  void armsNothingForReportThatIsNoWait(@TempDir Path dir) {
    StateService state = state(dir);
    state.putTask("ABC-42", taskAnswering("context", null));

    effects(state, InfraWaitProperties.defaults())
        .after(AgentStatusMessage.of("IN_PROGRESS", "working"), "ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().turnOrNone().mark(Wake.MARK)).isNull();
    verify(stops, never()).stop("ABC-42", TaskStop.Trigger.WAIT, "working");
  }

  @Test
  void refusesWaitWhereNoWaitIsConfigured(@TempDir Path dir) {
    ReportEffects off = effects(state(dir), new InfraWaitProperties(List.of()));

    MessageContext judged = off.judged(MessageContext.NONE);

    assertThat(waits.reportViolations(judged)).hasSize(1);
  }

  @Test
  void asksTheSessionAgainAtTheLastWaitOnceTheTaskStoppedAsWaiting(@TempDir Path dir) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(dir.toString())
                .withStateFile(dir.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    TaskTurns turns =
        new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn())));
    TaskStops realStops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(
                    new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReportEffects effects =
        new ReportEffects(
            new SessionWakes(state, turns, clock),
            turns,
            realStops,
            InfraWaitProperties.defaults(),
            mock(LinkRefusals.class));
    state.putTask("ABC-42", taskAnswering("wait-over", "3"));
    effects.after(waits, "ABC-42");

    Turn turn = state.task("ABC-42").orElseThrow().turnOrNone();
    assertThat(Wake.parse(turn.mark(Wake.MARK)))
        .contains(new Wake(4, NOW.plusSeconds(2700).toEpochMilli(), Step.WAIT_OVER));
  }

  @Test
  void keepsTheWaitStopAndTheNextWakeDueWhenTheSessionWaitsAgain(@TempDir Path dir) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(dir.toString())
                .withStateFile(dir.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    TaskTurns turns =
        new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn())));
    TaskStops realStops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(
                    new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReportEffects effects =
        new ReportEffects(
            new SessionWakes(state, turns, clock),
            turns,
            realStops,
            InfraWaitProperties.defaults(),
            mock(LinkRefusals.class));
    state.putTask("ABC-42", taskAnswering("wait-over", "3"));
    realStops.stop("ABC-42", TaskStop.Trigger.WAIT, "db down: ping db.internal");
    effects.after(waits, "ABC-42");

    Turn turn = state.task("ABC-42").orElseThrow().turnOrNone();
    assertThat(realStops.stopped("ABC-42")).isTrue();
    assertThat(Wake.parse(turn.mark(Wake.MARK)))
        .contains(new Wake(4, NOW.plusSeconds(2700).toEpochMilli(), Step.WAIT_OVER));
  }

  @Test
  void endsTheWaitStopWhenTheSessionReportsProgressWithoutWait(@TempDir Path dir) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(dir.toString())
                .withStateFile(dir.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    TaskTurns turns =
        new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn())));
    TaskStops realStops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(
                    new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReportEffects effects =
        new ReportEffects(
            new SessionWakes(state, turns, clock),
            turns,
            realStops,
            InfraWaitProperties.defaults(),
            mock(LinkRefusals.class));
    state.putTask("ABC-42", taskAnswering("wait-over", "3"));
    realStops.stop("ABC-42", TaskStop.Trigger.WAIT, "db down: ping db.internal");
    effects.after(AgentStatusMessage.of("IN_PROGRESS", "db is back"), "ABC-42");

    assertThat(realStops.stopped("ABC-42")).isFalse();
  }
}
