package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SessionWakesTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private static final Instant STOPPED = Instant.parse("2026-10-19T10:00:00Z");
  private static final Instant RESET = Instant.parse("2026-10-19T15:00:00Z");

  private static Clock at(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  private static UsageLimits reading(double percent, Optional<Instant> resetsAt) {
    SubscriptionUsage usage =
        () ->
            new SubscriptionUsage.Read(
                new SubscriptionUsage.Snapshot(
                    List.of(new SubscriptionUsage.Window("5h", percent, resetsAt)),
                    Optional.empty()));
    return new UsageLimits(Optional.of(usage));
  }

  @TempDir Path dir;

  private Path file;
  private StateService state;

  @BeforeEach
  void startsOneRunningTaskUnderTheTempState() {
    file = dir.resolve("state.json");
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(dir.toString())
                    .withStateFile(file.toString())),
            reducer,
            event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
  }

  private static SessionWakes sessionWakes(Path file, Instant now) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(Objects.requireNonNull(file.getParent()).toString())
                    .withStateFile(file.toString())),
            reducer, event -> {});
    return new SessionWakes(
        state,
        new TaskTurns(state, at(now), new Checkers(new TaskChart(Composition.builtIn()))),
        at(now));
  }

  private static UsageLimitWakes wakes(UsageLimits limits, boolean on, Path file, Instant now) {
    return new UsageLimitWakes(
        limits,
        UsageLimitsProperties.defaults().withWakeOnReset(on),
        sessionWakes(file, now),
        at(now));
  }

  @Test
  void wakesNothingBeforeTheWindowResets() {
    wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");

    List<SessionWakes.Due> due = sessionWakes(file, RESET.minusSeconds(1)).takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void wakesTheSessionOnceWhenTheWindowResets() {
    wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
    SessionWakes later = sessionWakes(file, RESET);

    List<SessionWakes.Due> first = later.takeDue(taskId -> false);
    List<SessionWakes.Due> second = later.takeDue(taskId -> false);

    assertThat(first)
        .containsExactly(
            new SessionWakes.Due(
                "ABC-42", Step.WAKE, new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
    assertThat(second).isEmpty();
  }

  @Test
  void keepsDueWakePendingWhileTheHumanIsTypingAndSendsItOnceAfter() {
    wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
    SessionWakes later = sessionWakes(file, RESET);

    List<SessionWakes.Due> whileTyping = later.takeDue(taskId -> true);
    List<SessionWakes.Due> afterSending = later.takeDue(taskId -> false);
    List<SessionWakes.Due> again = later.takeDue(taskId -> false);

    assertThat(whileTyping).isEmpty();
    assertThat(afterSending)
        .containsExactly(
            new SessionWakes.Due(
                "ABC-42", Step.WAKE, new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
    assertThat(again).isEmpty();
  }

  @Test
  void keepsDueWakePendingWhileTheTaskIsPausedAndSendsItAfterTheLift() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.PAUSE, 1, "you paused it"))
            .build());
    sessionWakes(file, STOPPED).arm("ABC-42", RESET, Step.WAIT_OVER);
    SessionWakes later = sessionWakes(file, RESET);

    List<SessionWakes.Due> whilePaused = later.takeDue(taskId -> false);
    state.updateTask("ABC-42", task -> task.lifted(2));
    List<SessionWakes.Due> afterLift = later.takeDue(taskId -> false);

    assertThat(whilePaused).isEmpty();
    assertThat(afterLift).extracting(SessionWakes.Due::step).containsExactly(Step.WAIT_OVER);
  }

  @Test
  void doesNotWakeSessionTwiceAcrossRestart() {
    wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
    sessionWakes(file, RESET).takeDue(taskId -> false);

    List<SessionWakes.Due> afterRestart =
        sessionWakes(file, RESET.plusSeconds(60)).takeDue(taskId -> false);

    assertThat(afterRestart).isEmpty();
  }

  @Test
  void keepsPendingWakeAcrossRestart() {
    wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");

    List<SessionWakes.Due> afterRestart = sessionWakes(file, RESET).takeDue(taskId -> false);

    assertThat(afterRestart)
        .containsExactly(
            new SessionWakes.Due(
                "ABC-42", Step.WAKE, new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
  }

  @Test
  void armsNoWakeWhenNoWindowIsAtItsLimit() {
    wakes(reading(99, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");

    List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void armsNoWakeWhenTheFullWindowReportsNoReset() {
    wakes(reading(100, Optional.empty()), true, file, STOPPED).arm("ABC-42");

    List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void armsNoWakeWhileTheKeyIsOff() {
    wakes(reading(100, Optional.of(RESET)), false, file, STOPPED).arm("ABC-42");

    List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void dropsTheWakeWhenTheSessionMovesAgain() {
    wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
    SessionWakes later = sessionWakes(file, RESET);

    later.cancel("ABC-42");

    assertThat(later.takeDue(taskId -> false)).isEmpty();
  }

  @Test
  void wakesAtTheLaterResetWhenTwoWindowsAreFull() {
    SubscriptionUsage usage =
        () ->
            new SubscriptionUsage.Read(
                new SubscriptionUsage.Snapshot(
                    List.of(
                        new SubscriptionUsage.Window("5h", 100, Optional.of(RESET)),
                        new SubscriptionUsage.Window(
                            "week", 100, Optional.of(RESET.plusSeconds(86400)))),
                    Optional.empty()));
    wakes(new UsageLimits(Optional.of(usage)), true, file, STOPPED).arm("ABC-42");

    List<SessionWakes.Due> atFirstReset = sessionWakes(file, RESET).takeDue(taskId -> false);

    assertThat(atFirstReset).isEmpty();
  }

  @Test
  void dropsWakeOnceAnotherTurnOpenedAfterItWasArmed() {
    wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
    new TaskTurns(state, at(STOPPED), new Checkers(new TaskChart(Composition.builtIn())))
        .opened("ABC-42", "context", "a1b2", TaskStatus.IN_PROGRESS);

    List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void wakesWithTheStepItWasArmedFor() {
    sessionWakes(file, STOPPED).arm("ABC-42", RESET, Step.WAIT_OVER);

    List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

    assertThat(due)
        .containsExactly(
            new SessionWakes.Due(
                "ABC-42", Step.WAIT_OVER, new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
  }
}
