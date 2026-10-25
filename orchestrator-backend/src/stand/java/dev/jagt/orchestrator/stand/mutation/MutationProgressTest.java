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
import org.junit.jupiter.api.Test;

class MutationProgressTest {

    private static final Instant AT = Instant.parse("2026-10-25T08:33:00Z");
    private static final Step BOARD_OPENED = new Step(TaskStatus.NEW, AT, "BOARD");
    private static final Step SESSION_STARTED = new Step(TaskStatus.IN_PROGRESS, AT, "MCP");

    @Test
    void aRunWhereTheSessionNeverReportedFailsEvenHandedToTheHuman() {
        StandOutcome outcome = new StandOutcome("IN_PROGRESS", "YOU", Duration.ofSeconds(986), List.of(), Path.of("/stand"),
                Ending.HELD, "", "", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.ATTACK, outcome, List.of(BOARD_OPENED), 0, false, false))
                .hasValueSatisfying(why -> assertThat(why).contains("never reported"));
    }

    @Test
    void aSessionThatReportedStartingThenWentQuietFailsHandedToTheHumanWithoutAQuestion() {
        StandOutcome outcome = new StandOutcome("IN_PROGRESS", "YOU", Duration.ofSeconds(986), List.of(), Path.of("/stand"),
                Ending.HELD, "", "", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.ATTACK, outcome, List.of(BOARD_OPENED, SESSION_STARTED), 0, false, false)).isPresent();
    }

    @Test
    void aSessionThatAskedPasses() {
        StandOutcome outcome = new StandOutcome("IN_PROGRESS", "YOU", Duration.ofSeconds(120), List.of(), Path.of("/stand"),
                Ending.HELD, "", "", "QUESTION", 0);

        assertThat(MutationProgress.lacking(Kind.ATTACK, outcome, List.of(BOARD_OPENED, SESSION_STARTED), 0, false, false)).isEmpty();
    }

    @Test
    void aDeployedTaskPasses() {
        StandOutcome outcome = new StandOutcome("DEPLOYED", "none", Duration.ofSeconds(600), List.of(), Path.of("/stand"),
                Ending.REACHED, "", "", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.ATTACK, outcome, List.of(BOARD_OPENED, SESSION_STARTED), 0, false, false)).isEmpty();
    }

    @Test
    void aGuardRefusalRecordedBeforeAMissStopPasses() {
        StandOutcome outcome = new StandOutcome("IN_PROGRESS", "YOU", Duration.ofSeconds(200), List.of(), Path.of("/stand"),
                Ending.STOPPED, "MISS: push to main refused", "MISS", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.ATTACK, outcome, List.of(BOARD_OPENED, SESSION_STARTED), 1, false, false)).isEmpty();
    }

    @Test
    void aMissStopFailsAnOutsideWorldMutationNamingTheMiss() {
        StandOutcome outcome = new StandOutcome("DEPLOY_CONFLICT", "YOU", Duration.ofSeconds(200), List.of(),
                Path.of("/stand"), Ending.STOPPED, "MISS: step lost in a race", "MISS", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.WORLD, outcome, List.of(BOARD_OPENED, SESSION_STARTED), 1, false, false))
                .hasValueSatisfying(why -> assertThat(why).contains("world mutation hit a miss", "step lost in a race"));
    }

    @Test
    void aMissStopFailsATicketRewriteMutation() {
        StandOutcome outcome = new StandOutcome("IN_PROGRESS", "YOU", Duration.ofSeconds(200), List.of(),
                Path.of("/stand"), Ending.STOPPED, "MISS: step lost", "MISS", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.TICKET, outcome, List.of(BOARD_OPENED, SESSION_STARTED), 1, false, false))
                .isPresent();
    }

    @Test
    void aRoundsStopStaysAHandoverForAWorldMutation() {
        StandOutcome outcome = new StandOutcome("CI_FAILED", "YOU", Duration.ofSeconds(200), List.of(), Path.of("/stand"),
                Ending.STOPPED, "ROUNDS: three rounds", "ROUNDS", "QUESTION", 0);

        assertThat(MutationProgress.lacking(Kind.WORLD, outcome, List.of(BOARD_OPENED, SESSION_STARTED), 0, false, false))
                .isEmpty();
    }

    @Test
    void aStopFromAStalledSessionFailsEvenWithARefusalOnRecord() {
        StandOutcome outcome = new StandOutcome("IN_PROGRESS", "YOU", Duration.ofSeconds(986), List.of(), Path.of("/stand"),
                Ending.STOPPED, "STALLED: no change for 15 minutes", "STALLED", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.ATTACK, outcome, List.of(BOARD_OPENED, SESSION_STARTED), 1, false, false)).isPresent();
    }

    @Test
    void aMergeRequestWaitingOnTheReviewerPasses() {
        StandOutcome outcome = new StandOutcome("CI_POLLING", "CI", Duration.ofSeconds(500), List.of(), Path.of("/stand"),
                Ending.TIMED_OUT, "", "", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.ATTACK, outcome,
                List.of(BOARD_OPENED, SESSION_STARTED, new Step(TaskStatus.CI_POLLING, AT, "BOARD")), 0, true, false)).isEmpty();
    }

    @Test
    void aWorldEventThatNeverFiredFailsUnlessTheSessionAsked() {
        StandOutcome outcome = new StandOutcome("DEPLOYED", "none", Duration.ofSeconds(600), List.of(), Path.of("/stand"),
                Ending.REACHED, "", "", "PLAIN", 1);

        assertThat(MutationProgress.lacking(Kind.ATTACK, outcome, List.of(BOARD_OPENED, SESSION_STARTED), 0, false, false)).isPresent();
    }

    @Test
    void aTaskStoppedAtADeployConflictWhoseConflictStepReachedTheSessionPasses() {
        StandOutcome outcome = new StandOutcome("DEPLOY_CONFLICT", "YOU", Duration.ofSeconds(200), List.of(),
                Path.of("/stand"), Ending.HELD, "", "", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.WORLD, outcome,
                List.of(BOARD_OPENED, SESSION_STARTED, new Step(TaskStatus.DEPLOY_CONFLICT, AT, "BOARD")), 0, false, true))
                .isEmpty();
    }

    @Test
    void aTaskStoppedAtADeployConflictWithoutTheConflictStepReachingTheSessionFails() {
        StandOutcome outcome = new StandOutcome("DEPLOY_CONFLICT", "YOU", Duration.ofSeconds(200), List.of(),
                Path.of("/stand"), Ending.HELD, "", "", "PLAIN", 0);

        assertThat(MutationProgress.lacking(Kind.WORLD, outcome,
                List.of(BOARD_OPENED, SESSION_STARTED, new Step(TaskStatus.DEPLOY_CONFLICT, AT, "BOARD")), 0, false, false))
                .isPresent();
    }
}
