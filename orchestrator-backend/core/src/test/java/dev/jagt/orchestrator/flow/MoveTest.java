package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    /**
     * Shipping it commits nothing and returns the task to CI_POLLING, where the auto-poll relays the very threads
     * that round answered. It stays legal for a human with their own reason, just not the advice.
     */
    @Test
    void doesNotAdviseAShipForAReviewRoundThatChangedNothing() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.NO_CHANGES, false));

        assertThat(move.primary()).isNull();
        assertThat(move.hint()).isEqualTo("nothing to ship — every comment answered; the open threads are the"
                + " reviewer's move");
        assertThat(move.actions()).contains(TaskAction.SHIP);
    }

    /** `ship` is the only thing that posts review_replies.md, so "nothing to ship" would strand the answers. */
    @Test
    void stillAdvisesAShipWhenTheRoundLeftRepliesToPostEvenThoughItChangedNoCode() {
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

    /** Read off every status there is rather than a sample, so a status added later cannot go unowned. */
    @Test
    void namesTheHumanAsTheOwnerOfExactlyTheStatusesThatWaitForOne() {
        var waitingOnYou = Arrays.stream(TaskStatus.values())
                .filter(status -> Move.forTask(status, false, RoundState.NONE).owner() == Owner.YOU).toList();

        assertThat(waitingOnYou).containsExactly(TaskStatus.REVIEW_PENDING, TaskStatus.CI_FAILED,
                TaskStatus.REVIEWED, TaskStatus.APPROVED, TaskStatus.DEPLOY_CONFLICT, TaskStatus.DEPLOYED,
                TaskStatus.REVERTED);
    }

    @ParameterizedTest
    @CsvSource({"REVIEW_PENDING,false,REVIEW", "CI_POLLING,true,CHECK", "REVIEWED,true,READY",
            "APPROVED,true,READY"})
    void collapsesTheFourStatusesThatAllReadAsReviewIntoDistinctPhases(TaskStatus status, boolean hasRequest,
                                                                      Phase phase) {
        assertThat(Move.forTask(status, hasRequest, RoundState.NONE).phase()).isEqualTo(phase);
    }

    /** A primary the action list does not contain leaves the board with nothing highlighted at all. */
    @Test
    void neverMakesDeployThePrimaryMoveOfATaskThatHasNoRequestToLand() {
        Move move = Move.forTask(TaskStatus.REVIEWED, false, RoundState.NONE);

        assertThat(move.actions()).doesNotContain(TaskAction.DEPLOY);
        assertThat(move.actions()).contains(move.primary());
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NOTHING", value = {"REVIEW_PENDING,false,SHIP", "CI_FAILED,true,SWEEP",
            "APPROVED,true,DEPLOY", "DEPLOYED,true,DONE", "IN_PROGRESS,false,FOCUS", "DONE,false,NOTHING"})
    void pointsAtTheOneObviousActionForEachStatus(TaskStatus status, boolean hasRequest, TaskAction primary) {
        assertThat(Move.forTask(status, hasRequest, RoundState.NONE).primary()).isEqualTo(primary);
    }

    /**
     * The projection answers "not live" rather than paying a process spawn per task per render, so a stuck task
     * still shows SHIP and the gate is what refuses when its agent turns out to be alive.
     */
    @Test
    void offersShipForATaskStuckAtShippingBecauseTheDeadAgentIsWhatMakesItStuck() {
        assertThat(Move.forTask(TaskStatus.SHIPPING, false, RoundState.NONE).actions()).contains(TaskAction.SHIP);
    }

    /** The change came back out, so the next move is a fix onto the same request — not the DONE a deploy gets. */
    @Test
    void advisesAFixOntoTheSameRequestForATaskWhoseDeployWasTakenBackOut() {
        Move move = Move.forTask(TaskStatus.REVERTED, true, RoundState.NONE);

        assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
        assertThat(move.actions()).contains(TaskAction.SHIP);
    }

    @Test
    void sendsTheHumanToTheAgentWhenAReversionHasNoRequestToShipOnto() {
        assertThat(Move.forTask(TaskStatus.REVERTED, false, RoundState.NONE).primary())
                .isEqualTo(TaskAction.FOCUS);
    }

    @Test
    void leavesAReversionInTheDeployPhaseBecauseThatIsWhereItWentWrong() {
        assertThat(Move.forTask(TaskStatus.REVERTED, true, RoundState.NONE).phase()).isEqualTo(Phase.DEPLOY);
    }
}
