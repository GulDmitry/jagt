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
        Move move = Move.forTask(status, false);

        assertThat(move.phase()).isNotNull();
        assertThat(move.owner()).isNotNull();
        assertThat(move.hint()).isNotBlank();
        assertThat(move.actions()).isNotEmpty();
    }

    @Test
    void namesTheHumanAsTheOwnerOfExactlyTheStatusesThatWaitForOne() {
        // This is the fact a person scans a board for, so it must be exhaustive rather than sampled.
        var waitingOnYou = Arrays.stream(TaskStatus.values())
                .filter(status -> Move.forTask(status, false).owner() == Owner.YOU).toList();

        assertThat(waitingOnYou).containsExactly(TaskStatus.REVIEW_PENDING, TaskStatus.CI_FAILED,
                TaskStatus.REVIEWED, TaskStatus.APPROVED, TaskStatus.DEPLOY_CONFLICT, TaskStatus.DEPLOYED);
    }

    @Test
    void collapsesTheFourStatusesThatAllReadAsReviewIntoDistinctPhases() {
        // The root of "ревью/шип/деплой непонятно": these four say "review" to a human but mean four things.
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, false).phase()).isEqualTo(Phase.REVIEW);
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true).phase()).isEqualTo(Phase.CHECK);
        assertThat(Move.forTask(TaskStatus.REVIEWED, true).phase()).isEqualTo(Phase.READY);
        assertThat(Move.forTask(TaskStatus.APPROVED, true).phase()).isEqualTo(Phase.READY);
    }

    @Test
    void offersShipOnlyWhereTheShipGateWouldAcceptIt() {
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, false).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.NEW, false).actions()).doesNotContain(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.DONE, true).actions()).doesNotContain(TaskAction.SHIP);
        // A further round onto an existing request is a ship; without a request there is nothing to ship onto.
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true).actions()).contains(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.CI_POLLING, false).actions()).doesNotContain(TaskAction.SHIP);
    }

    @Test
    void offersTheReviewSweepOnlyWhenThereIsSomethingToSweep() {
        assertThat(Move.forTask(TaskStatus.CI_POLLING, true).actions()).contains(TaskAction.SWEEP);
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false).actions()).doesNotContain(TaskAction.SWEEP);
    }

    @Test
    void offersDeployOnlyWhenTheChangeIsReadyOrStuckOnItsWayOut() {
        assertThat(Move.forTask(TaskStatus.REVIEWED, true).actions()).contains(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.APPROVED, true).actions()).contains(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.DEPLOY_CONFLICT, true).actions()).contains(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false).actions()).doesNotContain(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.DEPLOYED, true).actions()).doesNotContain(TaskAction.DEPLOY);
    }

    @Test
    void pointsAtTheOneObviousActionForEachStatus() {
        assertThat(Move.forTask(TaskStatus.REVIEW_PENDING, false).primary()).isEqualTo(TaskAction.SHIP);
        assertThat(Move.forTask(TaskStatus.CI_FAILED, true).primary()).isEqualTo(TaskAction.SWEEP);
        assertThat(Move.forTask(TaskStatus.APPROVED, true).primary()).isEqualTo(TaskAction.DEPLOY);
        assertThat(Move.forTask(TaskStatus.DEPLOYED, true).primary()).isEqualTo(TaskAction.DONE);
        assertThat(Move.forTask(TaskStatus.IN_PROGRESS, false).primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(Move.forTask(TaskStatus.DONE, false).primary()).isNull();
    }

    @Test
    void offersShipForATaskStuckAtShippingBecauseTheDeadAgentIsWhatMakesItStuck() {
        // Liveness is not an input to the projection (it would cost a process spawn per task per render), so
        // SHIP is offered and the gate refuses at execution time if the agent turns out to be alive.
        assertThat(Move.forTask(TaskStatus.SHIPPING, false).actions()).contains(TaskAction.SHIP);
        assertThat(Move.shippable(TaskStatus.SHIPPING, true, false)).isFalse();
        assertThat(Move.shippable(TaskStatus.SHIPPING, false, false)).isTrue();
    }
}
