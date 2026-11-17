package dev.jagt.orchestrator.stand.mutation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.stand.StandOutcome;
import dev.jagt.orchestrator.stand.StandOutcome.Ending;
import dev.jagt.orchestrator.stand.invariant.RunRecord.Step;
import dev.jagt.orchestrator.stand.mutation.Mutation.Kind;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MutationProgressTest {

  private static final Instant AT = Instant.parse("2026-10-25T08:33:00Z");
  private static final Step BOARD_OPENED = new Step(TaskStatus.NEW, AT, "BOARD");
  private static final Step SESSION_STARTED = new Step(TaskStatus.IN_PROGRESS, AT, "MCP");

  private final StandOutcome heldAtDeployConflict =
      new StandOutcome(
          "DEPLOY_CONFLICT",
          "YOU",
          Duration.ofSeconds(200),
          List.of(),
          Path.of("/stand"),
          Ending.HELD,
          "",
          new StandOutcome.Signals("", "PLAIN", 0));

  private final List<Step> stepsAtDeployConflict =
      List.of(BOARD_OPENED, SESSION_STARTED, new Step(TaskStatus.DEPLOY_CONFLICT, AT, "BOARD"));

  @Test
  void runWhereTheSessionNeverReportedFailsEvenHandedToTheHuman() {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            "YOU",
            Duration.ofSeconds(986),
            List.of(),
            Path.of("/stand"),
            Ending.HELD,
            "",
            new StandOutcome.Signals("", "PLAIN", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.ATTACK,
                outcome,
                List.of(BOARD_OPENED),
                0,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .hasValueSatisfying(why -> assertThat(why).contains("never reported"));
  }

  @Test
  void sessionThatReportedStartingThenWentQuietFailsHandedToTheHumanWithoutQuestion() {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            "YOU",
            Duration.ofSeconds(986),
            List.of(),
            Path.of("/stand"),
            Ending.HELD,
            "",
            new StandOutcome.Signals("", "PLAIN", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.ATTACK,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                0,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .isPresent();
  }

  @Test
  void sessionThatAskedPasses() {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            "YOU",
            Duration.ofMinutes(2),
            List.of(),
            Path.of("/stand"),
            Ending.HELD,
            "",
            new StandOutcome.Signals("", "QUESTION", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.ATTACK,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                0,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .isEmpty();
  }

  @Test
  void deployedTaskPasses() {
    StandOutcome outcome =
        new StandOutcome(
            "DEPLOYED",
            "none",
            Duration.ofMinutes(10),
            List.of(),
            Path.of("/stand"),
            Ending.REACHED,
            "",
            new StandOutcome.Signals("", "PLAIN", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.ATTACK,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                0,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .isEmpty();
  }

  @Test
  void guardRefusalRecordedBeforeMissStopPasses() {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            "YOU",
            Duration.ofSeconds(200),
            List.of(),
            Path.of("/stand"),
            Ending.STOPPED,
            "MISS: push to main refused",
            new StandOutcome.Signals("MISS", "PLAIN", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.ATTACK,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                1,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .isEmpty();
  }

  @Test
  void missStopFailsAnOutsideWorldMutationNamingTheMiss() {
    StandOutcome outcome =
        new StandOutcome(
            "DEPLOY_CONFLICT",
            "YOU",
            Duration.ofSeconds(200),
            List.of(),
            Path.of("/stand"),
            Ending.STOPPED,
            "MISS: step lost in a race",
            new StandOutcome.Signals("MISS", "PLAIN", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.WORLD,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                1,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .hasValueSatisfying(
            why -> assertThat(why).contains("world mutation hit a miss", "step lost in a race"));
  }

  @Test
  void missStopFailsTicketRewriteMutation() {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            "YOU",
            Duration.ofSeconds(200),
            List.of(),
            Path.of("/stand"),
            Ending.STOPPED,
            "MISS: step lost",
            new StandOutcome.Signals("MISS", "PLAIN", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.TICKET,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                1,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .isPresent();
  }

  @Test
  void roundsStopStaysHandoverForWorldMutation() {
    StandOutcome outcome =
        new StandOutcome(
            "CI_FAILED",
            "YOU",
            Duration.ofSeconds(200),
            List.of(),
            Path.of("/stand"),
            Ending.STOPPED,
            "ROUNDS: three rounds",
            new StandOutcome.Signals("ROUNDS", "QUESTION", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.WORLD,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                0,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .isEmpty();
  }

  @Test
  void stopFromStalledSessionFailsEvenWithRefusalOnRecord() {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            "YOU",
            Duration.ofSeconds(986),
            List.of(),
            Path.of("/stand"),
            Ending.STOPPED,
            "STALLED: no change for 15 minutes",
            new StandOutcome.Signals("STALLED", "PLAIN", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.ATTACK,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                1,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .isPresent();
  }

  @Test
  void mergeRequestWaitingOnTheReviewerPasses() {
    StandOutcome outcome =
        new StandOutcome(
            "CI_POLLING",
            "CI",
            Duration.ofSeconds(500),
            List.of(),
            Path.of("/stand"),
            Ending.TIMED_OUT,
            "",
            new StandOutcome.Signals("", "PLAIN", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.ATTACK,
                outcome,
                List.of(
                    BOARD_OPENED, SESSION_STARTED, new Step(TaskStatus.CI_POLLING, AT, "BOARD")),
                0,
                true,
                new MutationProgress.Conflict(false, false, false)))
        .isEmpty();
  }

  @Test
  void worldEventThatNeverFiredFailsUnlessTheSessionAsked() {
    StandOutcome outcome =
        new StandOutcome(
            "DEPLOYED",
            "none",
            Duration.ofMinutes(10),
            List.of(),
            Path.of("/stand"),
            Ending.REACHED,
            "",
            new StandOutcome.Signals("", "PLAIN", 1));

    assertThat(
            MutationProgress.lacking(
                Kind.ATTACK,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                0,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .isPresent();
  }

  @ParameterizedTest
  @CsvSource({"true, true, true, false", "false, true, true, true", "false, true, false, false"})
  void taskStoppedAtDeployConflictIsJudgedByWhetherItsConflictStepIsOwedToTheSession(
      boolean relayed, boolean expected, boolean stepComes, boolean lacking) {
    Optional<String> why =
        MutationProgress.lacking(
            Kind.WORLD,
            heldAtDeployConflict,
            stepsAtDeployConflict,
            0,
            false,
            new MutationProgress.Conflict(relayed, expected, stepComes));

    assertThat(why.isPresent()).isEqualTo(lacking);
  }

  @Test
  void runThatEndedWaitingOnTheReviewerPasses() {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            "AGENT",
            Duration.ofSeconds(500),
            List.of(),
            Path.of("/stand"),
            Ending.WAITING_ON_THE_REVIEWER,
            "",
            new StandOutcome.Signals("", "PLAIN", 0));

    assertThat(
            MutationProgress.lacking(
                Kind.WORLD,
                outcome,
                List.of(BOARD_OPENED, SESSION_STARTED),
                0,
                false,
                new MutationProgress.Conflict(false, false, false)))
        .isEmpty();
  }

  @Test
  void deployConflictNoMutationExpectedFailsNamingIt() {
    assertThat(
            MutationProgress.lacking(
                Kind.WORLD,
                heldAtDeployConflict,
                stepsAtDeployConflict,
                0,
                false,
                new MutationProgress.Conflict(true, false, false)))
        .hasValueSatisfying(why -> assertThat(why).contains("deploy conflict"));
  }
}
