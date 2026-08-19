package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.model.AgentReport;
import dev.jagt.orchestrator.model.Owner;
import dev.jagt.orchestrator.model.Phase;
import dev.jagt.orchestrator.model.RoundState;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskStatus;

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

    /** A primary the action list does not contain leaves the board with nothing highlighted at all. */
    @Test
    void neverMakesDeployThePrimaryMoveOfATaskThatHasNoRequestToLand() {
        Move move = Move.forTask(TaskStatus.REVIEWED, false, RoundState.NONE);

        assertThat(move.actions()).doesNotContain(TaskAction.DEPLOY);
        assertThat(move.actions()).contains(move.primary());
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

    /**
     * The projection answers "not live" rather than paying a process spawn per task per render, so a stuck task
     * still shows SHIP and the gate is what refuses when its agent turns out to be alive.
     */
    @Test
    void offersShipForATaskStuckAtShippingBecauseTheDeadAgentIsWhatMakesItStuck() {
        assertThat(Move.forTask(TaskStatus.SHIPPING, false, RoundState.NONE).actions()).contains(TaskAction.SHIP);
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
