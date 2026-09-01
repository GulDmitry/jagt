package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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

    @Test
    void doesNotAdviseAShipForAReviewRoundThatChangedNothing() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true,
                new RoundState(AgentReport.NO_CHANGES, false), false, watching());

        assertThat(move.primary()).isNull();
        assertThat(move.owner()).isEqualTo(Owner.CI);
        assertThat(move.hint()).isEqualTo("nothing to ship; the open threads are the reviewer's move");
        assertThat(move.actions()).contains(TaskAction.SHIP);
    }

    @Test
    void handsAReviewRoundBackToTheHumanOncePollingHasStoppedForIt() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true,
                new RoundState(AgentReport.NO_CHANGES, false), false, elapsed());

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
        assertThat(move.hint())
                .isEqualTo("nothing is polling this round; sweep reads the comments and checks now");
    }

    @Test
    void leavesATaskWithItsAgentWhenThePollingWindowElapsedWhileItWorks() {
        Move move = Move.forTask(TaskStatus.IN_PROGRESS, true, RoundState.NONE, false, elapsed());

        assertThat(move.owner()).isEqualTo(Owner.AGENT);
        assertThat(move.hint()).isEqualTo("agent is working; no action required");
    }

    @Test
    void stillAdvisesAShipWhenTheRoundLeftRepliesToPostEvenThoughItChangedNoCode() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.NO_CHANGES, true), false);

        assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
        assertThat(move.hint())
                .isEqualTo("no code changed; ship posts the drafted replies and nothing else");
    }

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
        assertThat(move.hint()).isEqualTo("read the round and the question it left (focus), then ship");
    }

    @Test
    void leavesAQuestionUnshoutedWhileAPollIsStillReadingTheRoundItWasAskedOn() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.QUESTION, false),
                false, watching());

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.attention()).isEqualTo(Attention.OPTIONAL);
        assertThat(move.ask()).isEqualTo("you can review the round");
    }

    @Test
    void interruptsForTheSameQuestionOnceNothingIsPollingTheRoundAnyMore() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.QUESTION, false),
                false, elapsed());

        assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"IN_PROGRESS", "CI_FAILED", "DEPLOY_CONFLICT", "APPROVED",
            "DEPLOYED"})
    void interruptsForAQuestionNoCommentOnTheRequestCanReachEvenWhileThatRequestIsPolled(TaskStatus status) {
        Move move = Move.forTask(status, true, new RoundState(AgentReport.QUESTION, false), false, watching());

        assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
    }

    @Test
    void asksForTheRoundToBeReadRatherThanTheSessionAnsweredWhenTheQuestionCameBackWithIt() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true, new RoundState(AgentReport.QUESTION, false),
                false);

        assertThat(move.ask()).isEqualTo("review the round");
    }

    @ParameterizedTest
    @CsvSource({"NEW", "IN_PROGRESS", "SHIPPING"})
    void handsAnAgentThatStoppedToAskBackToTheHumanInsteadOfSayingItIsStillWorking(TaskStatus status) {
        Move move = Move.forTask(status, false, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("answer the question");
        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    }

    @ParameterizedTest
    @CsvSource({"NEW", "IN_PROGRESS", "SHIPPING"})
    void handsAnAgentThatWentQuietBackToTheHumanInsteadOfSayingItIsStillWorking(TaskStatus status) {
        Move move = Move.forTask(status, false, RoundState.NONE, true);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("stopped without reporting");
        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    }

    @Test
    void quotesTheQuestionRatherThanTheSilenceWhenAnAgentAskedBeforeItStopped() {
        Move move = Move.forTask(TaskStatus.IN_PROGRESS, false,
                new RoundState(AgentReport.QUESTION, false), true);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("answer the question");
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEW_PENDING", "CI_POLLING", "DEPLOYED", "DONE"})
    void keepsTheOwnerOfAStatusThatWasNeverTheAgentsEvenIfSilenceWasStamped(TaskStatus status) {
        assertThat(Move.forTask(status, true, RoundState.NONE, true))
                .isEqualTo(Move.forTask(status, true, RoundState.NONE, false));
    }

    @Test
    void namesTheHumanAsTheOwnerOfExactlyTheStatusesThatWaitForOne() {
        var waitingOnYou = Arrays.stream(TaskStatus.values())
                .filter(status -> Move.forTask(status, true, RoundState.NONE, false).owner() == Owner.YOU).toList();

        assertThat(waitingOnYou).containsExactly(TaskStatus.REVIEW_PENDING, TaskStatus.CI_FAILED,
                TaskStatus.APPROVED, TaskStatus.DEPLOY_CONFLICT, TaskStatus.REVERTED);
    }

    @Test
    void asksForNothingOnceTheChangeIsLiveWhileStillOfferingTheClose() {
        Move move = Move.forTask(TaskStatus.DEPLOYED, true, RoundState.NONE, false);

        assertThat(move.owner()).isEqualTo(Owner.NOBODY);
        assertThat(move.primary()).isEqualTo(TaskAction.DONE);
    }

    @Test
    void stillAsksForTheHumanWhenAnAgentQuestionOutlivesTheDeploy() {
        Move move = Move.forTask(TaskStatus.DEPLOYED, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("answer the question");
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void saysNothingAboutAttentionUnlessTheMoveIsTheHumansOwn(TaskStatus status) {
        Move move = Move.forTask(status, true, RoundState.NONE, false);

        assertThat(move.attention() == Attention.NONE).isEqualTo(move.owner() != Owner.YOU);
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void namesTheActOnEveryCardWhoseMoveIsTheHumansOwn(TaskStatus status) {
        Move working = Move.forTask(status, true, RoundState.NONE, false);
        Move stopped = Move.forTask(status, true, RoundState.NONE, true);
        Move asked = Move.forTask(status, true, new RoundState(AgentReport.QUESTION, false), false);
        Move unpolled = Move.forTask(status, true, RoundState.NONE, false, elapsed());
        Move requestless = Move.forTask(status, false, RoundState.NONE, false);

        assertThat(working.ask() == null).isEqualTo(working.attention() == Attention.NONE);
        assertThat(stopped.ask() == null).isEqualTo(stopped.attention() == Attention.NONE);
        assertThat(asked.ask() == null).isEqualTo(asked.attention() == Attention.NONE);
        assertThat(unpolled.ask() == null).isEqualTo(unpolled.attention() == Attention.NONE);
        assertThat(requestless.ask() == null).isEqualTo(requestless.attention() == Attention.NONE);
    }

    @ParameterizedTest
    @CsvSource({
            "REVIEW_PENDING, review and ship",
            "CI_FAILED, relay the failed checks",
            "DEPLOY_CONFLICT, resolve the conflict"
    })
    void namesTheActItWantsRatherThanHowLoudlyItWantsIt(TaskStatus status, String ask) {
        assertThat(Move.forTask(status, true, RoundState.NONE, false).ask()).isEqualTo(ask);
    }

    @ParameterizedTest
    @CsvSource({
            "CI_POLLING, read the review",
            "REVIEWED, read the review"
    })
    void namesTheReadWhenTheRoundIsBackWithTheHumanBecauseNothingPollsIt(TaskStatus status, String ask) {
        assertThat(Move.forTask(status, true, RoundState.NONE, false, elapsed()).ask()).isEqualTo(ask);
    }

    @ParameterizedTest
    @CsvSource({
            "CI_POLLING, focus the agent",
            "CI_FAILED, focus the agent",
            "REVIEWED, close the task"
    })
    void namesWhatIsLeftWhenTheStatusClaimsAReviewRequestThatIsNotThere(TaskStatus status, String ask) {
        assertThat(Move.forTask(status, false, RoundState.NONE, false).ask()).isEqualTo(ask);
    }

    @ParameterizedTest
    @CsvSource({
            "APPROVED, you can deploy it",
            "REVERTED, you can ship a fix"
    })
    void offersRatherThanOrdersTheActWhoseCardCanWait(TaskStatus status, String ask) {
        assertThat(Move.forTask(status, true, RoundState.NONE, false).ask()).isEqualTo(ask);
    }

    @Test
    void asksForAnAnswerWhereverTheSessionStoppedToPutTheQuestion() {
        Move move = Move.forTask(TaskStatus.DEPLOYED, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.ask()).isEqualTo("answer the session");
    }

    @Test
    void asksForTheSessionToBeCheckedWhenItWentQuietWithoutReporting() {
        Move move = Move.forTask(TaskStatus.IN_PROGRESS, false, RoundState.NONE, true);

        assertThat(move.ask()).isEqualTo("check the stopped session");
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void highlightsAButtonForTheActItsBadgeNames(TaskStatus status) {
        List<Move> badged = Stream.of(
                        Move.forTask(status, true, RoundState.NONE, false),
                        Move.forTask(status, false, RoundState.NONE, false),
                        Move.forTask(status, true, RoundState.NONE, true),
                        Move.forTask(status, true, RoundState.NONE, false, elapsed()))
                .filter(move -> move.ask() != null).toList();

        assertThat(badged).allSatisfy(move -> assertThat(move.actions()).contains(move.primary()));
    }

    @Test
    void pointsAtTheSessionRatherThanAShipWhenARevertedDeployHasNoRequestToShipOnto() {
        Move move = Move.forTask(TaskStatus.REVERTED, false, RoundState.NONE, false);

        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(move.hint()).isEqualTo("the deploy was reverted and no request is open; focus the agent");
    }

    @Test
    void pointsAtTheSessionRatherThanASweepWhenAFailedRunHasNoRequestToReadItFrom() {
        Move move = Move.forTask(TaskStatus.CI_FAILED, false, RoundState.NONE, false);

        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
        assertThat(move.hint()).isEqualTo("no review request to read the failure from; focus the agent");
    }

    @Test
    void saysNothingAboutARoundWhoseOpenThreadsAreTheReviewersToClose() {
        Move move = Move.forTask(TaskStatus.REVIEW_PENDING, true,
                new RoundState(AgentReport.NO_CHANGES, false), false);

        assertThat(move.owner()).isEqualTo(Owner.CI);
        assertThat(move.attention()).isEqualTo(Attention.NONE);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"APPROVED", "REVERTED"})
    void offersTheNextMoveWithoutInterruptingWhenNothingIsStuck(TaskStatus status) {
        Move move = Move.forTask(status, true, RoundState.NONE, false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.attention()).isEqualTo(Attention.OPTIONAL);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEW_PENDING", "CI_FAILED", "DEPLOY_CONFLICT"})
    void interruptsForATaskThatMovesNoFurtherWithoutTheHuman(TaskStatus status) {
        assertThat(Move.forTask(status, true, RoundState.NONE, false).attention())
                .isEqualTo(Attention.REQUIRED);
    }

    @Test
    void interruptsWhenAnAgentAsksFromAStatusThatWouldOtherwiseWait() {
        Move move = Move.forTask(TaskStatus.APPROVED, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
    }

    @Test
    void interruptsForAnAgentThatWentQuietWithoutReportingAnything() {
        Move move = Move.forTask(TaskStatus.IN_PROGRESS, false, RoundState.NONE, true);

        assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
    }

    @Test
    void waitsOnTheReviewerAfterACleanRoundThatNobodyHasApprovedYet() {
        Move move = Move.forTask(TaskStatus.REVIEWED, true, RoundState.NONE, false, watching());

        assertThat(move.owner()).isEqualTo(Owner.CI);
        assertThat(move.primary()).isNull();
        assertThat(move.actions()).contains(TaskAction.DEPLOY);
        assertThat(move.hint()).contains("waiting for an approval");
    }

    @Test
    void highlightsTheReadWhenNothingIsPollingForTheApprovalAtAll() {
        Move move = Move.forTask(TaskStatus.REVIEWED, true, RoundState.NONE, false);

        assertThat(move.owner()).isEqualTo(Owner.CI);
        assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
        assertThat(move.hint()).contains("nothing is polling for the approval");
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEW_PENDING", "CI_FAILED", "REVIEWED", "APPROVED",
            "REVERTED", "DEPLOYED"})
    void highlightsTheAnswerRatherThanAVerbThatActsWhileAQuestionIsOpen(TaskStatus status) {
        Move move = Move.forTask(status, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    }

    @Test
    void handsACleanRoundBackToTheHumanOnceNothingPollsItForTheApproval() {
        Move move = Move.forTask(TaskStatus.REVIEWED, true, RoundState.NONE, false, elapsed());

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
        assertThat(move.hint()).contains("nothing is polling for the approval");
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"CI_POLLING", "REVIEWED"})
    void handsTheTaskOverForAQuestionAskedFromAStatusThatWaitsOnTheCodeHost(TaskStatus status) {
        Move move = Move.forTask(status, true, new RoundState(AgentReport.QUESTION, false), false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.hint()).contains("answer the question");
    }

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

    @Test
    void offersShipForATaskStuckAtShippingBecauseTheDeadAgentIsWhatMakesItStuck() {
        assertThat(Move.forTask(TaskStatus.SHIPPING, false, RoundState.NONE, false).actions())
                .contains(TaskAction.SHIP);
    }

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
