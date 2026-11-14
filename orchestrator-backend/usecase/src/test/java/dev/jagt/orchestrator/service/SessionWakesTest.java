package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.session.Standing;
import dev.jagt.orchestrator.port.agent.SubscriptionUsage;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.Step;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionWakesTest {

  private static final Instant STOPPED = Instant.parse("2026-10-19T10:00:00Z");
  private static final Instant RESET = Instant.parse("2026-10-19T15:00:00Z");

  @TempDir Path dir;

  private StateService state;
  private Checkers checkers;
  private Clock atStop;
  private Clock atReset;
  private SessionWakes wakesAtStop;
  private SessionWakes wakesAtReset;
  private UsageLimitWakes limitWakes;

  @BeforeEach
  void startsOneRunningTaskUnderTheTempState() {
    state = stateService().withRoot(dir).build();
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    checkers = new Checkers(new TaskChart(Composition.builtIn()));
    atStop = Clock.fixed(STOPPED, ZoneOffset.UTC);
    atReset = Clock.fixed(RESET, ZoneOffset.UTC);
    wakesAtStop = new SessionWakes(state, new TaskTurns(state, atStop, checkers), atStop);
    wakesAtReset = new SessionWakes(state, new TaskTurns(state, atReset, checkers), atReset);
    SubscriptionUsage usage =
        () ->
            new SubscriptionUsage.Read(
                new SubscriptionUsage.Snapshot(
                    List.of(new SubscriptionUsage.Window("5h", 100, Optional.of(RESET))),
                    Optional.empty()));
    limitWakes =
        new UsageLimitWakes(
            new UsageLimits(Optional.of(usage)),
            UsageLimitsProperties.defaults().withWakeOnReset(true),
            wakesAtStop,
            atStop);
  }

  @Test
  void wakesNothingBeforeTheWindowResets() {
    limitWakes.arm("ABC-42");
    Clock beforeReset = Clock.fixed(RESET.minusSeconds(1), ZoneOffset.UTC);

    List<SessionWakes.Due> due =
        new SessionWakes(state, new TaskTurns(state, beforeReset, checkers), beforeReset)
            .takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void wakesTheSessionOnceWhenTheWindowResets() {
    limitWakes.arm("ABC-42");
    SessionWakes later = wakesAtReset;

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
    limitWakes.arm("ABC-42");
    SessionWakes later = wakesAtReset;

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
    wakesAtStop.arm("ABC-42", RESET, Step.WAIT_OVER);
    SessionWakes later = wakesAtReset;

    List<SessionWakes.Due> whilePaused = later.takeDue(taskId -> false);
    state.updateTask("ABC-42", task -> task.lifted(2));
    List<SessionWakes.Due> afterLift = later.takeDue(taskId -> false);

    assertThat(whilePaused).isEmpty();
    assertThat(afterLift).extracting(SessionWakes.Due::step).containsExactly(Step.WAIT_OVER);
  }

  @Test
  void doesNotWakeSessionTwiceAcrossRestart() {
    limitWakes.arm("ABC-42");
    wakesAtReset.takeDue(taskId -> false);
    Clock later = Clock.fixed(RESET.plusSeconds(60), ZoneOffset.UTC);
    StateService restarted = stateService().withRoot(dir).build();

    List<SessionWakes.Due> afterRestart =
        new SessionWakes(restarted, new TaskTurns(restarted, later, checkers), later)
            .takeDue(taskId -> false);

    assertThat(afterRestart).isEmpty();
  }

  @Test
  void keepsPendingWakeAcrossRestart() {
    limitWakes.arm("ABC-42");
    StateService restarted = stateService().withRoot(dir).build();

    List<SessionWakes.Due> afterRestart =
        new SessionWakes(restarted, new TaskTurns(restarted, atReset, checkers), atReset)
            .takeDue(taskId -> false);

    assertThat(afterRestart)
        .containsExactly(
            new SessionWakes.Due(
                "ABC-42", Step.WAKE, new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
  }

  @Test
  void armsNoWakeWhenNoWindowIsAtItsLimit() {
    SubscriptionUsage usage =
        () ->
            new SubscriptionUsage.Read(
                new SubscriptionUsage.Snapshot(
                    List.of(new SubscriptionUsage.Window("5h", 99, Optional.of(RESET))),
                    Optional.empty()));
    new UsageLimitWakes(
            new UsageLimits(Optional.of(usage)),
            UsageLimitsProperties.defaults().withWakeOnReset(true),
            wakesAtStop,
            atStop)
        .arm("ABC-42");

    List<SessionWakes.Due> due = wakesAtReset.takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void armsNoWakeWhenTheFullWindowReportsNoReset() {
    SubscriptionUsage usage =
        () ->
            new SubscriptionUsage.Read(
                new SubscriptionUsage.Snapshot(
                    List.of(new SubscriptionUsage.Window("5h", 100, Optional.empty())),
                    Optional.empty()));
    new UsageLimitWakes(
            new UsageLimits(Optional.of(usage)),
            UsageLimitsProperties.defaults().withWakeOnReset(true),
            wakesAtStop,
            atStop)
        .arm("ABC-42");

    List<SessionWakes.Due> due = wakesAtReset.takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void armsNoWakeWhileTheKeyIsOff() {
    SubscriptionUsage usage =
        () ->
            new SubscriptionUsage.Read(
                new SubscriptionUsage.Snapshot(
                    List.of(new SubscriptionUsage.Window("5h", 100, Optional.of(RESET))),
                    Optional.empty()));
    new UsageLimitWakes(
            new UsageLimits(Optional.of(usage)),
            UsageLimitsProperties.defaults().withWakeOnReset(false),
            wakesAtStop,
            atStop)
        .arm("ABC-42");

    List<SessionWakes.Due> due = wakesAtReset.takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void dropsTheWakeWhenTheSessionMovesAgain() {
    limitWakes.arm("ABC-42");
    SessionWakes later = wakesAtReset;

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
    new UsageLimitWakes(
            new UsageLimits(Optional.of(usage)),
            UsageLimitsProperties.defaults().withWakeOnReset(true),
            wakesAtStop,
            atStop)
        .arm("ABC-42");

    List<SessionWakes.Due> atFirstReset = wakesAtReset.takeDue(taskId -> false);

    assertThat(atFirstReset).isEmpty();
  }

  @Test
  void dropsWakeOnceAnotherTurnOpenedAfterItWasArmed() {
    limitWakes.arm("ABC-42");
    new TaskTurns(state, atStop, checkers)
        .opened("ABC-42", "context", "a1b2", TaskStatus.IN_PROGRESS);

    List<SessionWakes.Due> due = wakesAtReset.takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void wakesWithTheStepItWasArmedFor() {
    wakesAtStop.arm("ABC-42", RESET, Step.WAIT_OVER);

    List<SessionWakes.Due> due = wakesAtReset.takeDue(taskId -> false);

    assertThat(due)
        .containsExactly(
            new SessionWakes.Due(
                "ABC-42", Step.WAIT_OVER, new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
  }
}
