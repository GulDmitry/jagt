package dev.jagt.orchestrator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MoveTest {

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void answersForEveryStatusThereIsSoNoTaskCanRenderAsUnknown(TaskStatus status) {
        Move move = Move.forTask(status, false, AgentReport.PLAIN);

        assertThat(move.phase()).isNotNull();
        assertThat(move.owner()).isNotNull();
        assertThat(move.hint()).isNotBlank();
        assertThat(move.actions()).isNotEmpty();
    }

    @Test
    void doesNotAdviseAShipForAReviewRoundThatChangedNothing() {
        // Shipping it commits nothing and returns the task to CI_POLLING, where the auto-poll relays the very
        // threads that round answered — the ping-pong. Nothing is highlighted instead.
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, AgentReport.NO_CHANGES);

        assertThat(move.primary()).isNull();
        assertThat(move.hint()).isEqualTo("nothing to ship — every comment answered; the open threads are the"
                + " reviewer's move");
        // Still legal, for a human who has their own reason to ship — just not the advice.
        assertThat(move.actions()).contains(TaskAction.SHIP);
    }

    @Test
    void pointsAtTheAgentWhenTheRoundEndedInAQuestionInsteadOfAtTheShipButton() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, AgentReport.QUESTION);

        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(move.hint()).contains("the agent is asking");
    }

    @Test
    void namesTheHumanAsTheOwnerOfExactlyTheStatusesThatWaitForOne() {
        // This is the fact a person scans a board for, so it must be exhaustive rather than sampled.
        var waitingOnYou = Arrays.stream(TaskStatus.values())
                .filter(status -> Move.forTask(status, false, AgentReport.PLAIN).owner() == Owner.YOU).toList();

        assertThat(waitingOnYou).containsExactly(TaskStatus.REVIEW_PENDING, TaskStatus.CI_FAILED,
                TaskStatus.REVIEWED, TaskStatus.APPROVED, TaskStatus.DEPLOY_CONFLICT, TaskStatus.DEPLOYED,
                TaskStatus.REVERTED);
    }

    @Test
    void collapsesTheFourStatusesThatAllReadAsReviewIntoDistinctPhases() {
        // The root of "review/ship/deploy are all a blur": these four say "review" to a human but mean four things.
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, false, AgentReport.PLAIN).phase()).isEqualTo(Phase.REVIEW);
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true, AgentReport.PLAIN).phase()).isEqualTo(Phase.CHECK);
        assertThat(Move.forTask(TaskStatus.REVIEWED, true, AgentReport.PLAIN).phase()).isEqualTo(Phase.READY);
        assertThat(Move.forTask(TaskStatus.APPROVED, true, AgentReport.PLAIN).phase()).isEqualTo(Phase.READY);
    }

    @Test
    void offersShipOnlyWhereTheShipGateWouldAcceptIt() {
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false, AgentReport.PLAIN).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, false, AgentReport.PLAIN).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.NEW, false, AgentReport.PLAIN).actions()).doesNotContain(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.DONE, true, AgentReport.PLAIN).actions()).doesNotContain(TaskAction.SHIP);
        // A further round onto an existing request is a ship; without a request there is nothing to ship onto.
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true, AgentReport.PLAIN).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.CI_POLLING, false, AgentReport.PLAIN).actions()).doesNotContain(TaskAction.SHIP);
    }

    @Test
    void offersTheReviewSweepOnlyWhenThereIsSomethingToSweep() {
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true, AgentReport.PLAIN).actions()).contains(TaskAction.SWEEP);
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false, AgentReport.PLAIN).actions()).doesNotContain(TaskAction.SWEEP);
    }

    @Test
    void offersDeployOnlyWhenTheChangeIsReadyOrStuckOnItsWayOut() {
        assertThat(Move.forTask(TaskStatus.REVIEWED, true, AgentReport.PLAIN).actions()).contains(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.APPROVED, true, AgentReport.PLAIN).actions()).contains(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.DEPLOY_CONFLICT, true, AgentReport.PLAIN).actions()).contains(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false, AgentReport.PLAIN).actions()).doesNotContain(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.DEPLOYED, true, AgentReport.PLAIN).actions()).doesNotContain(TaskAction.DEPLOY);
    }

    @Test
    void pointsAtTheOneObviousActionForEachStatus() {
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, false, AgentReport.PLAIN).primary()).isEqualTo(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.CI_FAILED, true, AgentReport.PLAIN).primary()).isEqualTo(TaskAction.SWEEP);
        assertThat(Move.forTask(TaskStatus.APPROVED, true, AgentReport.PLAIN).primary()).isEqualTo(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.DEPLOYED, true, AgentReport.PLAIN).primary()).isEqualTo(TaskAction.DONE);
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false, AgentReport.PLAIN).primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(Move.forTask(TaskStatus.DONE, false, AgentReport.PLAIN).primary()).isNull();
    }

    @Test
    void offersShipForATaskStuckAtShippingBecauseTheDeadAgentIsWhatMakesItStuck() {
        // Liveness is not an input to the projection (it would cost a process spawn per task per render), so
        // SHIP is offered and the gate refuses at execution time if the agent turns out to be alive.
        assertThat(Move.forTask(TaskStatus.SHIPPING, false, AgentReport.PLAIN).actions()).contains(TaskAction.SHIP);
        assertThat(Move.shippable(TaskStatus.SHIPPING, true, false)).isFalse();
        assertThat(Move.shippable(TaskStatus.SHIPPING, false, false)).isTrue();
    }

    @Test
    void offersRevertOnlyForATaskWhoseDeployActuallyLanded() {
        assertThat(Move.forTask(TaskStatus.DEPLOYED, true, AgentReport.PLAIN).actions()).contains(TaskAction.REVERT);
        // A conflicted deploy never merged, and a reverted one has nothing left to take back out.
        assertThat(Move.forTask(TaskStatus.DEPLOY_CONFLICT, true, AgentReport.PLAIN).actions()).doesNotContain(TaskAction.REVERT);
        assertThat(Move.forTask(TaskStatus.REVERTED, true, AgentReport.PLAIN).actions()).doesNotContain(TaskAction.REVERT);
        assertThat(Move.forTask(TaskStatus.REVIEWED, true, AgentReport.PLAIN).actions()).doesNotContain(TaskAction.REVERT);
    }

    @Test
    void treatsAReversionAsWorkToRedoRatherThanATaskToClose() {
        // The change came back out, so the expected next move is a fix onto the same review request — not DONE,
        // which is what a DEPLOYED task gets.
        assertThat(Move.forTask(TaskStatus.REVERTED, true, AgentReport.PLAIN).primary()).isEqualTo(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.REVERTED, true, AgentReport.PLAIN).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.REVERTED, false, AgentReport.PLAIN).primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(Move.forTask(TaskStatus.REVERTED, true, AgentReport.PLAIN).phase()).isEqualTo(Phase.DEPLOY);
    }
}
