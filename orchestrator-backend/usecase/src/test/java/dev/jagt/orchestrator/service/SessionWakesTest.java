package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestSessionWakesBuilder.sessionWakes;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.TestUsageLimitWakesBuilder.usageLimitWakes;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskChart;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionWakesTest {

  private static final Instant STOPPED = Instant.parse("2026-10-19T10:00:00Z");
  private static final Instant RESET = Instant.parse("2026-10-19T15:00:00Z");

  @TempDir Path dir;

  private StateService state;

  @BeforeEach
  void startsOneRunningTaskUnderTheTempState() {
    state = stateService().withRoot(dir).build();
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
  }

  @Test
  void wakesNothingBeforeTheWindowResets() {
    usageLimitWakes().withRoot(dir).build().arm("ABC-42");

    List<SessionWakes.Due> due =
        sessionWakes()
            .withRoot(dir)
            .withNow(RESET.minusSeconds(1))
            .build()
            .takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void wakesTheSessionOnceWhenTheWindowResets() {
    usageLimitWakes().withRoot(dir).build().arm("ABC-42");
    SessionWakes later = sessionWakes().withRoot(dir).withNow(RESET).build();

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
    usageLimitWakes().withRoot(dir).build().arm("ABC-42");
    SessionWakes later = sessionWakes().withRoot(dir).withNow(RESET).build();

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
    sessionWakes().withRoot(dir).build().arm("ABC-42", RESET, Step.WAIT_OVER);
    SessionWakes later = sessionWakes().withRoot(dir).withNow(RESET).build();

    List<SessionWakes.Due> whilePaused = later.takeDue(taskId -> false);
    state.updateTask("ABC-42", task -> task.lifted(2));
    List<SessionWakes.Due> afterLift = later.takeDue(taskId -> false);

    assertThat(whilePaused).isEmpty();
    assertThat(afterLift).extracting(SessionWakes.Due::step).containsExactly(Step.WAIT_OVER);
  }

  @Test
  void doesNotWakeSessionTwiceAcrossRestart() {
    usageLimitWakes().withRoot(dir).build().arm("ABC-42");
    sessionWakes().withRoot(dir).withNow(RESET).build().takeDue(taskId -> false);

    List<SessionWakes.Due> afterRestart =
        sessionWakes()
            .withRoot(dir)
            .withNow(RESET.plusSeconds(60))
            .build()
            .takeDue(taskId -> false);

    assertThat(afterRestart).isEmpty();
  }

  @Test
  void keepsPendingWakeAcrossRestart() {
    usageLimitWakes().withRoot(dir).build().arm("ABC-42");

    List<SessionWakes.Due> afterRestart =
        sessionWakes().withRoot(dir).withNow(RESET).build().takeDue(taskId -> false);

    assertThat(afterRestart)
        .containsExactly(
            new SessionWakes.Due(
                "ABC-42", Step.WAKE, new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
  }

  @Test
  void armsNoWakeWhenNoWindowIsAtItsLimit() {
    usageLimitWakes().withRoot(dir).withPercent(99).build().arm("ABC-42");

    List<SessionWakes.Due> due =
        sessionWakes().withRoot(dir).withNow(RESET).build().takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void armsNoWakeWhenTheFullWindowReportsNoReset() {
    usageLimitWakes().withRoot(dir).withResetsAt(Optional.empty()).build().arm("ABC-42");

    List<SessionWakes.Due> due =
        sessionWakes().withRoot(dir).withNow(RESET).build().takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void armsNoWakeWhileTheKeyIsOff() {
    usageLimitWakes().withRoot(dir).withWakeOnReset(false).build().arm("ABC-42");

    List<SessionWakes.Due> due =
        sessionWakes().withRoot(dir).withNow(RESET).build().takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void dropsTheWakeWhenTheSessionMovesAgain() {
    usageLimitWakes().withRoot(dir).build().arm("ABC-42");
    SessionWakes later = sessionWakes().withRoot(dir).withNow(RESET).build();

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
            sessionWakes().withRoot(dir).build(),
            Clock.fixed(STOPPED, ZoneOffset.UTC))
        .arm("ABC-42");

    List<SessionWakes.Due> atFirstReset =
        sessionWakes().withRoot(dir).withNow(RESET).build().takeDue(taskId -> false);

    assertThat(atFirstReset).isEmpty();
  }

  @Test
  void dropsWakeOnceAnotherTurnOpenedAfterItWasArmed() {
    usageLimitWakes().withRoot(dir).build().arm("ABC-42");
    new TaskTurns(
            state,
            Clock.fixed(STOPPED, ZoneOffset.UTC),
            new Checkers(new TaskChart(Composition.builtIn())))
        .opened("ABC-42", "context", "a1b2", TaskStatus.IN_PROGRESS);

    List<SessionWakes.Due> due =
        sessionWakes().withRoot(dir).withNow(RESET).build().takeDue(taskId -> false);

    assertThat(due).isEmpty();
  }

  @Test
  void wakesWithTheStepItWasArmedFor() {
    sessionWakes().withRoot(dir).build().arm("ABC-42", RESET, Step.WAIT_OVER);

    List<SessionWakes.Due> due =
        sessionWakes().withRoot(dir).withNow(RESET).build().takeDue(taskId -> false);

    assertThat(due)
        .containsExactly(
            new SessionWakes.Due(
                "ABC-42", Step.WAIT_OVER, new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
  }
}
