package dev.jagt.orchestrator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoveTest {

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void answersForEveryStatusThereIsSoNoTaskCanRenderAsUnknown(TaskStatus status) {
        Move move = Move.forTask(status, false, RoundState.NONE);

        assertThat(move.phase()).isNotNull();
        assertThat(move.owner()).isNotNull();
        assertThat(move.hint()).isNotBlank();
        assertThat(move.actions()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void offersWhatMovesTheTaskOnBeforeWhatOnlyLooksAtIt(TaskStatus status) {
        List<TaskAction> actions = Move.forTask(status, true, RoundState.NONE).actions();

        assertThat(actions).isSortedAccordingTo(Comparator.comparing(TaskAction::group));
        assertThat(actions).endsWith(TaskAction.FOCUS, TaskAction.IDE, TaskAction.DIFF, TaskAction.RESPAWN);
    }

    @Test
    void doesNotAdviseAShipForAReviewRoundThatChangedNothing() {
        // Shipping it commits nothing and returns the task to CI_POLLING, where the auto-poll relays the very
        // threads that round answered — the ping-pong. Nothing is highlighted instead.
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.NO_CHANGES, false));

        assertThat(move.primary()).isNull();
        assertThat(move.hint()).isEqualTo("nothing to ship — every comment answered; the open threads are the"
                + " reviewer's move");
        // Still legal, for a human who has their own reason to ship — just not the advice.
        assertThat(move.actions()).contains(TaskAction.SHIP);
    }

    @Test
    void stillAdvisesAShipWhenTheRoundLeftRepliesToPostEvenThoughItChangedNoCode() {
        // `ship` is the only thing that posts review_replies.md, so "nothing to ship" would strand the answers
        // and leave the reviewer waiting on threads the agent already answered.
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.NO_CHANGES, true));

        assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
        assertThat(move.hint()).isEqualTo("no code changed — ship to post the drafted replies, nothing else"
                + " goes out");
    }

    @Test
    void pointsAtTheAgentWhenTheRoundEndedInAQuestionInsteadOfAtTheShipButton() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.QUESTION, false));

        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(move.hint()).contains("the agent is asking");
    }

    @Test
    void namesTheHumanAsTheOwnerOfExactlyTheStatusesThatWaitForOne() {
        // This is the fact a person scans a board for, so it must be exhaustive rather than sampled.
        var waitingOnYou = Arrays.stream(TaskStatus.values())
                .filter(status -> Move.forTask(status, false, RoundState.NONE).owner() == Owner.YOU).toList();

        assertThat(waitingOnYou).containsExactly(TaskStatus.REVIEW_PENDING, TaskStatus.CI_FAILED,
                TaskStatus.REVIEWED, TaskStatus.APPROVED, TaskStatus.DEPLOY_CONFLICT, TaskStatus.DEPLOYED,
                TaskStatus.REVERTED);
    }

    @Test
    void collapsesTheFourStatusesThatAllReadAsReviewIntoDistinctPhases() {
        // The root of "review/ship/deploy are all a blur": these four say "review" to a human but mean four things.
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, false, RoundState.NONE).phase()).isEqualTo(Phase.REVIEW);
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true, RoundState.NONE).phase()).isEqualTo(Phase.CHECK);
        assertThat(Move.forTask(TaskStatus.REVIEWED, true, RoundState.NONE).phase()).isEqualTo(Phase.READY);
        assertThat(Move.forTask(TaskStatus.APPROVED, true, RoundState.NONE).phase()).isEqualTo(Phase.READY);
    }

    @Test
    void offersShipOnlyWhereTheShipGateWouldAcceptIt() {
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false, RoundState.NONE).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, false, RoundState.NONE).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.NEW, false, RoundState.NONE).actions()).doesNotContain(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.DONE, true, RoundState.NONE).actions()).doesNotContain(TaskAction.SHIP);
        // A further round onto an existing request is a ship; without a request there is nothing to ship onto.
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true, RoundState.NONE).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.CI_POLLING, false, RoundState.NONE).actions()).doesNotContain(TaskAction.SHIP);
    }

    @Test
    void offersTheReviewSweepOnlyWhenThereIsSomethingToSweep() {
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true, RoundState.NONE).actions()).contains(TaskAction.SWEEP);
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false, RoundState.NONE).actions()).doesNotContain(TaskAction.SWEEP);
    }

    /** The reviewer's verdict is the human's business, not a gate: an open request is something to land. */
    @Test
    void offersDeployOnAnyTaskWithARequestOpenWhateverTheReviewSaid() {
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, true, RoundState.NONE).actions())
                .contains(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true, RoundState.NONE).actions())
                .contains(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.APPROVED, true, RoundState.NONE).actions())
                .contains(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.DEPLOYED, true, RoundState.NONE).actions())
                .contains(TaskAction.DEPLOY);
    }

    /** A primary the action list does not contain leaves the board with nothing highlighted at all. */
    @Test
    void neverMakesDeployThePrimaryMoveOfATaskThatHasNoRequestToLand() {
        Move move = Move.forTask(TaskStatus.REVIEWED, false, RoundState.NONE);

        assertThat(move.actions()).doesNotContain(TaskAction.DEPLOY);
        assertThat(move.actions()).contains(move.primary());
    }

    /**
     * The exclusions are not "no request yet": an agent mid-round would have its branch merged out from under it,
     * and a reverted deploy has nothing the deploy branch does not already carry, so it could only refuse.
     */
    @Test
    void offersNoDeployWhereItCouldOnlyRaceTheAgentOrRefuse() {
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, true, RoundState.NONE).actions())
                .doesNotContain(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.REVERTED, true, RoundState.NONE).actions())
                .doesNotContain(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.NEW, true, RoundState.NONE).actions())
                .doesNotContain(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.SHIPPING, true, RoundState.NONE).actions())
                .doesNotContain(TaskAction.DEPLOY);
    }

    /** A stalled deploy is finished by deploying again, whether or not a request was ever read. */
    @Test
    void offersDeployOnAStalledDeployWithNoRequestAtAll() {
        assertThat(Move.forTask(TaskStatus.DEPLOY_CONFLICT, false, RoundState.NONE).actions())
                .contains(TaskAction.DEPLOY);
    }

    @Test
    void pointsAtTheOneObviousActionForEachStatus() {
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, false, RoundState.NONE).primary()).isEqualTo(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.CI_FAILED, true, RoundState.NONE).primary()).isEqualTo(TaskAction.SWEEP);
        assertThat(Move.forTask(TaskStatus.APPROVED, true, RoundState.NONE).primary()).isEqualTo(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.DEPLOYED, true, RoundState.NONE).primary()).isEqualTo(TaskAction.DONE);
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false, RoundState.NONE).primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(Move.forTask(TaskStatus.DONE, false, RoundState.NONE).primary()).isNull();
    }

    @Test
    void offersShipForATaskStuckAtShippingBecauseTheDeadAgentIsWhatMakesItStuck() {
        // Liveness is not an input to the projection (it would cost a process spawn per task per render), so
        // SHIP is offered and the gate refuses at execution time if the agent turns out to be alive.
        assertThat(Move.forTask(TaskStatus.SHIPPING, false, RoundState.NONE).actions()).contains(TaskAction.SHIP);
        assertThat(Move.shippable(TaskStatus.SHIPPING, true, false)).isFalse();
        assertThat(Move.shippable(TaskStatus.SHIPPING, false, false)).isTrue();
    }

    @Test
    void offersRevertOnlyForATaskWhoseDeployActuallyLanded() {
        assertThat(Move.forTask(TaskStatus.DEPLOYED, true, RoundState.NONE).actions()).contains(TaskAction.REVERT);
        // A conflicted deploy never merged, and a reverted one has nothing left to take back out.
        assertThat(Move.forTask(TaskStatus.DEPLOY_CONFLICT, true, RoundState.NONE).actions()).doesNotContain(TaskAction.REVERT);
        assertThat(Move.forTask(TaskStatus.REVERTED, true, RoundState.NONE).actions()).doesNotContain(TaskAction.REVERT);
        assertThat(Move.forTask(TaskStatus.REVIEWED, true, RoundState.NONE).actions()).doesNotContain(TaskAction.REVERT);
    }

    @Test
    void treatsAReversionAsWorkToRedoRatherThanATaskToClose() {
        // The change came back out, so the expected next move is a fix onto the same review request — not DONE,
        // which is what a DEPLOYED task gets.
        assertThat(Move.forTask(TaskStatus.REVERTED, true, RoundState.NONE).primary()).isEqualTo(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.REVERTED, true, RoundState.NONE).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.REVERTED, false, RoundState.NONE).primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(Move.forTask(TaskStatus.REVERTED, true, RoundState.NONE).phase()).isEqualTo(Phase.DEPLOY);
    }
}
