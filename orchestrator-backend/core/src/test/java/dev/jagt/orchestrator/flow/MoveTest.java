package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import dev.jagt.orchestrator.task.AutoReviewWatch;

import static org.assertj.core.api.Assertions.assertThat;

class MoveTest {

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void answersForEveryStatusThereIsSoNoTaskCanRenderAsUnknown(TaskStatus status) {
        Move move = Move.forTask(status, false, RoundState.NONE, false);

        assertThat(move.phase()).isNotNull();
        assertThat(move.owner()).isNotNull();
        assertThat(move.hint()).isNotBlank();
        assertThat(move.actions()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void offersWhatMovesTheTaskOnBeforeWhatOnlyLooksAtIt(TaskStatus status) {
        List<TaskAction> actions = Move.forTask(status, true, RoundState.NONE, false).actions();

        assertThat(actions).isSortedAccordingTo(Comparator.comparing(TaskAction::group));
        assertThat(actions).endsWith(TaskAction.FOCUS, TaskAction.IDE, TaskAction.DIFF, TaskAction.RESPAWN);
    }

    /**
     * Shipping it commits nothing and returns the task to CI_POLLING, where the auto-poll relays the very threads
     * that round answered. It stays legal for a human with their own reason, just not the advice.
     */
    @Test
    void doesNotAdviseAShipForAReviewRoundThatChangedNothing() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true,
                new RoundState(AgentReport.NO_CHANGES, false), false, watching());

        assertThat(move.primary()).isNull();
        assertThat(move.owner()).isEqualTo(Owner.CI);
        assertThat(move.hint()).isEqualTo("nothing to ship; the open threads are the reviewer's move");
        assertThat(move.actions()).contains(TaskAction.SHIP);
    }

    /**
     * The threads this round is waiting on are read by the poller, so a round it has stopped polling waits on
     * NOBODY — the card said "the reviewer's move" and nothing would ever look at that request again.
     */
    @Test
    void handsAReviewRoundBackToTheHumanOncePollingHasStoppedForIt() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true,
                new RoundState(AgentReport.NO_CHANGES, false), false, elapsed());

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
        assertThat(move.hint())
                .isEqualTo("nothing is polling this round; sweep reads the comments and checks now");
    }

    /** An agent at work is not waiting on a poller, so a window that has elapsed says nothing about its card. */
    @Test
    void leavesATaskWithItsAgentWhenThePollingWindowElapsedWhileItWorks() {
        Move move = Move.forTask(TaskStatus.IN_PROGRESS, true, RoundState.NONE, false, elapsed());

        assertThat(move.owner()).isEqualTo(Owner.AGENT);
        assertThat(move.hint()).isEqualTo("agent is working; no action required");
    }

    /** `ship` is the only thing that posts review_replies.md, so "nothing to ship" would strand the answers. */
    @Test
    void stillAdvisesAShipWhenTheRoundLeftRepliesToPostEvenThoughItChangedNoCode() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.NO_CHANGES, true), false);

        assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
        assertThat(move.hint())
                .isEqualTo("no code changed; ship posts the drafted replies and nothing else");
    }

    /** Nothing is out for review yet, so there is no reviewer to wait for — the ship opens the request. */
    @Test
    void asksTheHumanToShipARoundThatChangedNothingBeforeAnyRequestExists() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, false, new RoundState(AgentReport.NO_CHANGES, false),
                false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
        assertThat(move.hint()).contains("ship opens the review request");
    }

    @Test
    void keepsTheRoundWithTheHumanWhenTheDraftedRepliesStillNeedAShip() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.NO_CHANGES, true),
                false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
    }

    /** Waiting on the host is only true while something is still reading the round for you. */
    @Test
    void asksTheHumanToSweepARoundThePollHasGivenUpOn() {
        Move move = Move.forTask(TaskStatus.CI_POLLING, true, RoundState.NONE, false, elapsed());

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("nothing is polling this round");
    }

    @Test
    void leavesARoundStillBeingPolledWithTheHost() {
        Move move = Move.forTask(TaskStatus.CI_POLLING, true, RoundState.NONE, false, watching());

        assertThat(move.owner()).isEqualTo(Owner.CI);
    }

    /** A host that has never seen the task cannot be what it is waiting for, and `sweep` is refused without one. */
    @Test
    void asksTheHumanAboutATaskWaitingOnChecksWithNoRequestToRead() {
        Move move = Move.forTask(TaskStatus.CI_POLLING, false, RoundState.NONE, false, watching());

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("no review request");
        assertThat(move.actions()).doesNotContain(TaskAction.SWEEP);
    }

    @Test
    void pointsAtTheAgentWhenTheRoundEndedInAQuestionInsteadOfAtTheShipButton() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(move.hint()).contains("answer the question");
    }

    @ParameterizedTest
    @CsvSource({"NEW", "IN_PROGRESS", "SHIPPING"})
    void handsAnAgentThatStoppedToAskBackToTheHumanInsteadOfSayingItIsStillWorking(TaskStatus status) {
        Move move = Move.forTask(status, false, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("answer the question");
        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    }

    /**
     * The watchdog probed and found nothing alive; the status still says the agent is working. A card that keeps
     * reading "agent" is dropped by the board's own-move filter and count, so the block stays invisible.
     */
    @ParameterizedTest
    @CsvSource({"NEW", "IN_PROGRESS", "SHIPPING"})
    void handsAnAgentThatWentQuietBackToTheHumanInsteadOfSayingItIsStillWorking(TaskStatus status) {
        Move move = Move.forTask(status, false, RoundState.NONE, true);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("stopped without reporting");
        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    }

    /** The question it managed to report is the more useful of the two, and the silence adds nothing to it. */
    @Test
    void quotesTheQuestionRatherThanTheSilenceWhenAnAgentAskedBeforeItStopped() {
        Move move = Move.forTask(TaskStatus.IN_PROGRESS, false,
                new RoundState(AgentReport.QUESTION, false), true);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("answer the question");
    }

    /** A status whose wait is the human's or the host's says nothing new when an agent stamp lingers on it. */
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEW_PENDING", "CI_POLLING", "DEPLOYED", "DONE"})
    void keepsTheOwnerOfAStatusThatWasNeverTheAgentsEvenIfSilenceWasStamped(TaskStatus status) {
        assertThat(Move.forTask(status, true, RoundState.NONE, true))
                .isEqualTo(Move.forTask(status, true, RoundState.NONE, false));
    }

    /**
     * Read off every status there is rather than a sample, so a status added later cannot go unowned. An open
     * request for all of them, because the question is which STATUS waits for a human — the cells that need more
     * than a status are asserted one by one above.
     */
    @Test
    void namesTheHumanAsTheOwnerOfExactlyTheStatusesThatWaitForOne() {
        var waitingOnYou = Arrays.stream(TaskStatus.values())
                .filter(status -> Move.forTask(status, true, RoundState.NONE, false).owner() == Owner.YOU).toList();

        assertThat(waitingOnYou).containsExactly(TaskStatus.REVIEW_PENDING, TaskStatus.CI_FAILED,
                TaskStatus.APPROVED, TaskStatus.DEPLOY_CONFLICT, TaskStatus.REVERTED);
    }

    /**
     * The change is live and closing the task is housekeeping: an install that badged this taught the human that
     * the badge means nothing, which costs the stalled session and the deploy conflict their only signal.
     */
    @Test
    void asksForNothingOnceTheChangeIsLiveWhileStillOfferingTheClose() {
        Move move = Move.forTask(TaskStatus.DEPLOYED, true, RoundState.NONE, false);

        assertThat(move.owner()).isEqualTo(Owner.NOBODY);
        assertThat(move.primary()).isEqualTo(TaskAction.DONE);
    }

    /** A session that stopped to ask is the human's whatever its task has already landed. */
    @Test
    void stillAsksForTheHumanWhenAnAgentQuestionOutlivesTheDeploy() {
        Move move = Move.forTask(TaskStatus.DEPLOYED, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("answer the question");
    }

    /**
     * A round that came back clean is not approved — that is the status after it — so the wait is a reviewer's,
     * and the deploy stays offered without being advised for whoever needs no approval.
     */
    @Test
    void waitsOnTheReviewerAfterACleanRoundThatNobodyHasApprovedYet() {
        Move move = Move.forTask(TaskStatus.REVIEWED, true, RoundState.NONE, false, watching());

        assertThat(move.owner()).isEqualTo(Owner.CI);
        assertThat(move.primary()).isNull();
        assertThat(move.actions()).contains(TaskAction.DEPLOY);
        assertThat(move.hint()).contains("waiting for an approval");
    }

    /**
     * An install with auto-review off polls nothing at all, so the approval is fetched only when a human asks:
     * a card with no highlighted move would leave them looking at a state nothing was ever going to change.
     */
    @Test
    void highlightsTheReadWhenNothingIsPollingForTheApprovalAtAll() {
        Move move = Move.forTask(TaskStatus.REVIEWED, true, RoundState.NONE, false);

        assertThat(move.owner()).isEqualTo(Owner.CI);
        assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
        assertThat(move.hint()).contains("nothing is polling for the approval");
    }

    /**
     * The answer is what unblocks the session, and the status alone would highlight a verb that ACTS: a ship on a
     * round the agent said it cannot finish, a deploy on the thing being asked about.
     */
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEW_PENDING", "CI_FAILED", "REVIEWED", "APPROVED",
            "REVERTED", "DEPLOYED"})
    void highlightsTheAnswerRatherThanAVerbThatActsWhileAQuestionIsOpen(TaskStatus status) {
        Move move = Move.forTask(status, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    }

    /** Nothing else will notice the approval, so reading it becomes the human's move. */
    @Test
    void handsACleanRoundBackToTheHumanOnceNothingPollsItForTheApproval() {
        Move move = Move.forTask(TaskStatus.REVIEWED, true, RoundState.NONE, false, elapsed());

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
        assertThat(move.hint()).contains("nothing is polling for the approval");
    }

    /**
     * Asking is stopping, and the statuses an agent is not EXPECTED to ask from are exactly the ones nothing else
     * flips: without this the question reaches no badge, no count and no notification.
     */
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"CI_POLLING", "REVIEWED"})
    void handsTheTaskOverForAQuestionAskedFromAStatusThatWaitsOnTheCodeHost(TaskStatus status) {
        Move move = Move.forTask(status, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("answer the question");
    }

    /** A closed task's leftover message is not a question anybody still owes an answer to. */
    @Test
    void leavesAClosedTaskAloneWhateverItsLastMessageSaid() {
        Move move = Move.forTask(TaskStatus.DONE, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.owner()).isEqualTo(Owner.NOBODY);
    }

    @ParameterizedTest
    @CsvSource({"REVIEW_PENDING,false,REVIEW", "CI_POLLING,true,CHECK", "REVIEWED,true,READY",
            "APPROVED,true,READY"})
    void collapsesTheFourStatusesThatAllReadAsReviewIntoDistinctPhases(TaskStatus status, boolean hasRequest,
                                                                      Phase phase) {
        assertThat(Move.forTask(status, hasRequest, RoundState.NONE, false).phase()).isEqualTo(phase);
    }

    /** A primary the action list does not contain leaves the board with nothing highlighted at all. */
    @Test
    void neverMakesDeployThePrimaryMoveOfATaskThatHasNoRequestToLand() {
        Move move = Move.forTask(TaskStatus.REVIEWED, false, RoundState.NONE, false);

        assertThat(move.actions()).doesNotContain(TaskAction.DEPLOY);
        assertThat(move.actions()).contains(move.primary());
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NOTHING", value = {"REVIEW_PENDING,false,SHIP", "CI_FAILED,true,SWEEP",
            "APPROVED,true,DEPLOY", "DEPLOYED,true,DONE", "IN_PROGRESS,false,FOCUS", "DONE,false,NOTHING"})
    void pointsAtTheOneObviousActionForEachStatus(TaskStatus status, boolean hasRequest, TaskAction primary) {
        assertThat(Move.forTask(status, hasRequest, RoundState.NONE, false).primary()).isEqualTo(primary);
    }

    /**
     * The projection answers "not live" rather than paying a process spawn per task per render, so a stuck task
     * still shows SHIP and the gate is what refuses when its agent turns out to be alive.
     */
    @Test
    void offersShipForATaskStuckAtShippingBecauseTheDeadAgentIsWhatMakesItStuck() {
        assertThat(Move.forTask(TaskStatus.SHIPPING, false, RoundState.NONE, false).actions())
                .contains(TaskAction.SHIP);
    }

    /** The change came back out, so the next move is a fix onto the same request — not the DONE a deploy gets. */
    @Test
    void advisesAFixOntoTheSameRequestForATaskWhoseDeployWasTakenBackOut() {
        Move move = Move.forTask(TaskStatus.REVERTED, true, RoundState.NONE, false);

        assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
        assertThat(move.actions()).contains(TaskAction.SHIP);
    }

    @Test
    void sendsTheHumanToTheAgentWhenAReversionHasNoRequestToShipOnto() {
        assertThat(Move.forTask(TaskStatus.REVERTED, false, RoundState.NONE, false).primary())
                .isEqualTo(TaskAction.FOCUS);
    }

    @Test
    void leavesAReversionInTheDeployPhaseBecauseThatIsWhereItWentWrong() {
        assertThat(Move.forTask(TaskStatus.REVERTED, true, RoundState.NONE, false).phase()).isEqualTo(Phase.DEPLOY);
    }
    private static AutoReviewWatch watching() {
        return AutoReviewWatch.watching(System.currentTimeMillis() + 60_000);
    }

    private static AutoReviewWatch elapsed() {
        return AutoReviewWatch.windowElapsed(24);
    }

}
