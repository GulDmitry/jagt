package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Attention;
import dev.jagt.orchestrator.flow.view.Owner;
import dev.jagt.orchestrator.flow.view.Phase;
import dev.jagt.orchestrator.flow.view.RoundState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.AutoReviewWatch;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MoveTest {

  private final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  @ParameterizedTest
  @EnumSource(TaskStatus.class)
  void answersForEveryStatusThereIsSoNoTaskCanRenderAsUnknown(TaskStatus status) {
    Move move = Move.forTask(rules, status, Facts.defaults(), RoundState.NONE, false);

    assertThat(move.phase()).isNotNull();
    assertThat(move.owner()).isNotNull();
    assertThat(move.hint()).isNotBlank();
    assertThat(move.actions()).isNotEmpty();
  }

  @ParameterizedTest
  @EnumSource(TaskStatus.class)
  void withholdsShipWhereEveryRequestIsMerged(TaskStatus status) {
    List<TaskAction> actions =
        Move.forTask(
                rules,
                status,
                Facts.defaults()
                    .withDelivery(
                        Facts.Delivery.defaults()
                            .withHasReviewRequest(true)
                            .withRequestMerged(true)),
                RoundState.NONE,
                false)
            .actions();

    assertThat(actions).doesNotContain(TaskAction.SHIP);
  }

  @ParameterizedTest
  @EnumSource(TaskStatus.class)
  void offersWhatMovesTheTaskOnBeforeWhatOnlyLooksAtIt(TaskStatus status) {
    List<TaskAction> actions =
        Move.forTask(
                rules,
                status,
                Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                RoundState.NONE,
                false)
            .actions();

    assertThat(actions).isSortedAccordingTo(Comparator.comparing(TaskAction::group));
    assertThat(actions)
        .endsWith(TaskAction.FOCUS, TaskAction.IDE, TaskAction.DIFF, TaskAction.RESPAWN);
  }

  @ParameterizedTest
  @CsvSource({
    "NEW, true",
    "IN_PROGRESS, true",
    "SHIPPING, true",
    "VERIFYING, false",
    "REVIEW_PENDING, false",
    "CI_POLLING, false"
  })
  void callsTurnEndUnreportedOnlyWhileTheMoveIsStillTheAgents(
      TaskStatus status, boolean unreported) {
    assertThat(Move.endsUnreported(status)).isEqualTo(unreported);
  }

  @ParameterizedTest
  @CsvSource({
    "DEPLOYED, true",
    "APPROVED, true",
    "CI_FAILED, true",
    "REVERTED, false",
    "DEPLOY_CONFLICT, false",
    "DONE, false"
  })
  void measuresTurnByTheTreeAtEveryStatusButTheEndedOnes(TaskStatus status, boolean onTheTree) {
    assertThat(Move.endsOnTheTree(status)).isEqualTo(onTheTree);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEW_PENDING", "PLAN_PENDING"})
  void leavesWhatTheMasterHasYetToReadOffTheHumansMoves(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults(),
            RoundState.of(AgentReport.PLAIN, false).withMasterReading(true),
            false);

    assertThat(move.owner()).isEqualTo(Owner.AGENT);
    assertThat(move.attention()).isEqualTo(Attention.NONE);
    assertThat(move.hint()).isEqualTo("the Master is reading this round; no action required");
  }

  @Test
  void leadsWithTheRevertAndSaysSoWhereTheConflictIsRevertsAndNotDeploys() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.DEPLOY_CONFLICT,
            Facts.defaults()
                .withDelivery(
                    Facts.Delivery.defaults()
                        .withHasReviewRequest(true)
                        .withLiveDeploy(true)
                        .withRevertConflict(true)),
            RoundState.NONE,
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.REVERT);
    assertThat(move.hint()).endsWith("git add, then revert");
  }

  @Test
  void doesNotAdviseShipForReviewRoundThatChangedNothing() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.NO_CHANGES, false),
            false,
            AutoReviewWatch.watching(System.currentTimeMillis() + 60_000));

    assertThat(move.primary()).isNull();
    assertThat(move.owner()).isEqualTo(Owner.CI);
    assertThat(move.hint()).isEqualTo("nothing to ship; the open threads are the reviewer's move");
    assertThat(move.actions()).contains(TaskAction.SHIP);
  }

  @Test
  void handsReviewRoundBackToTheHumanOncePollingHasStoppedForIt() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.NO_CHANGES, false),
            false,
            AutoReviewWatch.windowElapsed(24));

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
    assertThat(move.hint())
        .isEqualTo("nothing is polling this round; sweep reads the comments and checks now");
    assertThat(move.ask()).isEqualTo("read the review");
  }

  @Test
  void leavesTaskWithItsAgentWhenThePollingWindowElapsedWhileItWorks() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.IN_PROGRESS,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.windowElapsed(24));

    assertThat(move.owner()).isEqualTo(Owner.AGENT);
    assertThat(move.hint()).isEqualTo("agent is working; no action required");
  }

  @Test
  void stillAdvisesShipWhenTheRoundLeftRepliesToPostEvenThoughItChangedNoCode() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.NO_CHANGES, true),
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
    assertThat(move.hint())
        .isEqualTo("no code changed; ship posts the drafted replies and nothing else");
  }

  @Test
  void asksTheHumanToShipRoundThatChangedNothingBeforeAnyRequestExists() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults(),
            RoundState.of(AgentReport.NO_CHANGES, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
    assertThat(move.hint()).contains("ship opens the review request");
    assertThat(move.ask()).isEqualTo("open the review request");
  }

  @Test
  void keepsTheRoundWithTheHumanWhenTheDraftedRepliesStillNeedShip() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.NO_CHANGES, true),
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.ask()).isEqualTo("post the drafted replies");
  }

  @Test
  void asksTheHumanToSweepRoundThePollHasGivenUpOn() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.CI_POLLING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.windowElapsed(24));

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.hint()).contains("nothing is polling this round");
  }

  @Test
  void leavesRoundStillBeingPolledWithTheHost() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.CI_POLLING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.watching(System.currentTimeMillis() + 60_000));

    assertThat(move.owner()).isEqualTo(Owner.CI);
  }

  @Test
  void asksTheHumanAboutTaskWaitingOnChecksWithNoRequestToRead() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.CI_POLLING,
            Facts.defaults(),
            RoundState.NONE,
            false,
            AutoReviewWatch.watching(System.currentTimeMillis() + 60_000));

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.hint()).contains("no review request");
    assertThat(move.actions()).doesNotContain(TaskAction.SWEEP);
  }

  @Test
  void pointsAtTheAgentWhenTheRoundEndedInQuestionInsteadOfAtTheShipButton() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    assertThat(move.hint())
        .isEqualTo("read the round and the question it left in the Session tab, then ship");
  }

  @Test
  void leavesQuestionUnshoutedWhilePollIsStillReadingTheRoundItWasAskedOn() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false,
            AutoReviewWatch.watching(System.currentTimeMillis() + 60_000));

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.attention()).isEqualTo(Attention.OPTIONAL);
    assertThat(move.ask()).isEqualTo("you can review the round");
  }

  @Test
  void interruptsForTheSameQuestionOnceNothingIsPollingTheRoundAnyMore() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false,
            AutoReviewWatch.windowElapsed(24));

    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "CI_FAILED", "DEPLOY_CONFLICT", "APPROVED", "DEPLOYED"})
  void interruptsForQuestionNoCommentOnTheRequestCanReachEvenWhileThatRequestIsPolled(
      TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false,
            AutoReviewWatch.watching(System.currentTimeMillis() + 60_000));

    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
  }

  @Test
  void asksForTheRoundToBeReadRatherThanTheSessionAnsweredWhenTheQuestionCameBackWithIt() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.ask()).isEqualTo("review the round");
  }

  @ParameterizedTest
  @CsvSource({"NEW", "IN_PROGRESS", "SHIPPING"})
  void handsAnAgentThatStoppedToAskBackToTheHumanInsteadOfSayingItIsStillWorking(
      TaskStatus status) {
    Move move =
        Move.forTask(
            rules, status, Facts.defaults(), RoundState.of(AgentReport.QUESTION, false), false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.hint()).contains("answer the question");
    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
  }

  @ParameterizedTest
  @CsvSource({"NEW", "IN_PROGRESS", "SHIPPING"})
  void handsAnAgentThatWentQuietBackToTheHumanInsteadOfSayingItIsStillWorking(TaskStatus status) {
    Move move = Move.forTask(rules, status, Facts.defaults(), RoundState.NONE, true);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.hint()).contains("stopped without reporting");
    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
  }

  @Test
  void quotesTheQuestionRatherThanTheSilenceWhenAnAgentAskedBeforeItStopped() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.IN_PROGRESS,
            Facts.defaults(),
            RoundState.of(AgentReport.QUESTION, false),
            true);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.hint()).contains("answer the question");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEW_PENDING", "CI_POLLING", "DEPLOYED", "DONE"})
  void keepsTheOwnerOfStatusThatWasNeverTheAgentsEvenIfSilenceWasStamped(TaskStatus status) {
    assertThat(
            Move.forTask(
                rules,
                status,
                Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                RoundState.NONE,
                true))
        .isEqualTo(
            Move.forTask(
                rules,
                status,
                Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                RoundState.NONE,
                false));
  }

  @Test
  void namesTheHumanAsTheOwnerOfExactlyTheStatusesThatWaitForOne() {
    var waitingOnYou =
        Arrays.stream(TaskStatus.values())
            .filter(status ->
                Move.forTask(
                            rules,
                            status,
                            Facts.defaults()
                                .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                            RoundState.NONE,
                            false)
                        .owner()
                    == Owner.YOU)
            .toList();

    assertThat(waitingOnYou)
        .containsExactly(
            TaskStatus.PLAN_PENDING,
            TaskStatus.REVIEW_PENDING,
            TaskStatus.CI_FAILED,
            TaskStatus.APPROVED,
            TaskStatus.DEPLOY_CONFLICT,
            TaskStatus.REVERTED);
  }

  @Test
  void asksForNothingOnceTheChangeIsLiveWhileStillOfferingTheClose() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.DEPLOYED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.owner()).isEqualTo(Owner.NOBODY);
    assertThat(move.primary()).isEqualTo(TaskAction.DONE);
  }

  @Test
  void stillAsksForTheHumanWhenAnAgentQuestionOutlivesTheDeploy() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.DEPLOYED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.hint()).contains("answer the question");
  }

  @ParameterizedTest
  @EnumSource(TaskStatus.class)
  void saysNothingAboutAttentionUnlessTheMoveIsTheHumansOwn(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.attention() == Attention.NONE).isEqualTo(move.owner() != Owner.YOU);
  }

  @ParameterizedTest
  @EnumSource(TaskStatus.class)
  void namesTheActOnEveryCardWhoseMoveIsTheHumansOwn(TaskStatus status) {
    Move working =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false);
    Move stopped =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            true);
    Move asked =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);
    Move unpolled =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.windowElapsed(24));
    Move requestless = Move.forTask(rules, status, Facts.defaults(), RoundState.NONE, false);

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
    assertThat(
            Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                    RoundState.NONE,
                    false)
                .ask())
        .isEqualTo(ask);
  }

  @Test
  void namesTheReadWhenTheRoundIsBackWithTheHumanBecauseNothingPollsIt() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.CI_POLLING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.windowElapsed(24));

    assertThat(move.ask()).isEqualTo("read the review");
  }

  @Test
  void asksForTheApprovalRatherThanReadOnRoundThatCameBackWithNothingUnresolved() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEWED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.windowElapsed(24));

    assertThat(move.ask()).isEqualTo("check for the approval");
  }

  @ParameterizedTest
  @CsvSource({
    "CI_POLLING, open the Session tab",
    "CI_FAILED, open the Session tab",
    "REVIEWED, close the task"
  })
  void namesWhatIsLeftWhenTheStatusClaimsReviewRequestThatIsNotThere(
      TaskStatus status, String ask) {
    assertThat(Move.forTask(rules, status, Facts.defaults(), RoundState.NONE, false).ask())
        .isEqualTo(ask);
  }

  @ParameterizedTest
  @CsvSource({"APPROVED, you can deploy it", "REVERTED, you can ship a fix"})
  void offersRatherThanOrdersTheActWhoseCardCanWait(TaskStatus status, String ask) {
    assertThat(
            Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                    RoundState.NONE,
                    false)
                .ask())
        .isEqualTo(ask);
  }

  @Test
  void asksForAnAnswerWhereverTheSessionStoppedToPutTheQuestion() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.DEPLOYED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.ask()).isEqualTo("answer the session");
  }

  @Test
  void asksForTheSessionToBeCheckedWhenItWentQuietWithoutReporting() {
    Move move =
        Move.forTask(rules, TaskStatus.IN_PROGRESS, Facts.defaults(), RoundState.NONE, true);

    assertThat(move.ask()).isEqualTo("check the stopped session");
  }

  @ParameterizedTest
  @EnumSource(TaskStatus.class)
  void highlightsButtonForTheActItsBadgeNames(TaskStatus status) {
    List<Move> badged =
        Stream.of(
                Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                    RoundState.NONE,
                    false),
                Move.forTask(rules, status, Facts.defaults(), RoundState.NONE, false),
                Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                    RoundState.NONE,
                    true),
                Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                    RoundState.NONE,
                    false,
                    AutoReviewWatch.windowElapsed(24)))
            .filter(move -> move.ask() != null)
            .toList();

    assertThat(badged).allSatisfy(move -> assertThat(move.actions()).contains(move.primary()));
  }

  @Test
  void pointsAtTheSessionRatherThanShipWhenRevertedDeployHasNoRequestToShipOnto() {
    Move move = Move.forTask(rules, TaskStatus.REVERTED, Facts.defaults(), RoundState.NONE, false);

    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    assertThat(move.hint())
        .isEqualTo("the deploy was reverted and no request is open; see the Session tab");
  }

  @Test
  void pointsAtTheSessionRatherThanSweepWhenFailedRunHasNoRequestToReadItFrom() {
    Move move = Move.forTask(rules, TaskStatus.CI_FAILED, Facts.defaults(), RoundState.NONE, false);

    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    assertThat(move.hint())
        .isEqualTo("no review request to read the failure from; see the Session tab");
  }

  @Test
  void saysNothingAboutRoundWhoseOpenThreadsAreTheReviewersToClose() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.NO_CHANGES, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.CI);
    assertThat(move.attention()).isEqualTo(Attention.NONE);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"APPROVED", "REVERTED"})
  void offersTheNextMoveWithoutInterruptingWhenNothingIsStuck(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.attention()).isEqualTo(Attention.OPTIONAL);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEW_PENDING", "CI_FAILED", "DEPLOY_CONFLICT"})
  void interruptsForTaskThatMovesNoFurtherWithoutTheHuman(TaskStatus status) {
    assertThat(
            Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                    RoundState.NONE,
                    false)
                .attention())
        .isEqualTo(Attention.REQUIRED);
  }

  @Test
  void interruptsWhenAnAgentAsksFromStatusThatWouldOtherwiseWait() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.APPROVED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
  }

  @Test
  void interruptsForAnAgentThatWentQuietWithoutReportingAnything() {
    Move move =
        Move.forTask(rules, TaskStatus.IN_PROGRESS, Facts.defaults(), RoundState.NONE, true);

    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
  }

  @Test
  void waitsOnTheReviewerAfterCleanRoundThatNobodyHasApprovedYet() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEWED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.watching(System.currentTimeMillis() + 60_000));

    assertThat(move.owner()).isEqualTo(Owner.CI);
    assertThat(move.primary()).isNull();
    assertThat(move.actions()).contains(TaskAction.DEPLOY);
    assertThat(move.hint()).contains("waiting for an approval");
  }

  @Test
  void highlightsTheReadWhenNothingIsPollingForTheApprovalAtAll() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEWED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.owner()).isEqualTo(Owner.CI);
    assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
    assertThat(move.hint()).contains("nothing is polling for the approval");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEW_PENDING", "CI_FAILED", "REVIEWED", "APPROVED", "REVERTED", "DEPLOYED"})
  void highlightsTheAnswerRatherThanVerbThatActsWhileQuestionIsOpen(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
  }

  @Test
  void handsCleanRoundBackToTheHumanOnceNothingPollsItForTheApproval() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEWED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.windowElapsed(24));

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
    assertThat(move.hint()).contains("nothing is polling for the approval");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"CI_POLLING", "REVIEWED"})
  void handsTheTaskOverForQuestionAskedFromStatusThatWaitsOnTheCodeHost(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.hint()).contains("answer the question");
  }

  @Test
  void leavesClosedTaskAloneWhateverItsLastMessageSaid() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.DONE,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.NOBODY);
  }

  @ParameterizedTest
  @CsvSource({
    "REVIEW_PENDING,false,REVIEW",
    "CI_POLLING,true,CHECK",
    "REVIEWED,true,READY",
    "APPROVED,true,READY"
  })
  void collapsesTheFourStatusesThatAllReadAsReviewIntoDistinctPhases(
      TaskStatus status, boolean hasRequest, Phase phase) {
    assertThat(
            Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(hasRequest)),
                    RoundState.NONE,
                    false)
                .phase())
        .isEqualTo(phase);
  }

  @Test
  void neverMakesDeployThePrimaryMoveOfTaskThatHasNoRequestToLand() {
    Move move = Move.forTask(rules, TaskStatus.REVIEWED, Facts.defaults(), RoundState.NONE, false);

    assertThat(move.actions()).doesNotContain(TaskAction.DEPLOY);
    assertThat(move.actions()).contains(move.primary());
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NOTHING",
      value = {
        "REVIEW_PENDING,false,SHIP",
        "CI_FAILED,true,SWEEP",
        "APPROVED,true,DEPLOY",
        "DEPLOYED,true,DONE",
        "IN_PROGRESS,false,FOCUS",
        "DONE,false,NOTHING"
      })
  void pointsAtTheOneObviousActionForEachStatus(
      TaskStatus status, boolean hasRequest, TaskAction primary) {
    assertThat(
            Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(hasRequest)),
                    RoundState.NONE,
                    false)
                .primary())
        .isEqualTo(primary);
  }

  @Test
  void offersShipForTaskStuckAtShippingBecauseTheDeadAgentIsWhatMakesItStuck() {
    assertThat(
            Move.forTask(rules, TaskStatus.SHIPPING, Facts.defaults(), RoundState.NONE, false)
                .actions())
        .contains(TaskAction.SHIP);
  }

  @Test
  void advisesFixOntoTheSameRequestForTaskWhoseDeployWasTakenBackOut() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVERTED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
    assertThat(move.actions()).contains(TaskAction.SHIP);
  }

  @Test
  void sendsTheHumanToTheAgentWhenReversionHasNoRequestToShipOnto() {
    assertThat(
            Move.forTask(rules, TaskStatus.REVERTED, Facts.defaults(), RoundState.NONE, false)
                .primary())
        .isEqualTo(TaskAction.FOCUS);
  }

  @Test
  void leavesReversionInTheDeployPhaseBecauseThatIsWhereItWentWrong() {
    assertThat(
            Move.forTask(
                    rules,
                    TaskStatus.REVERTED,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                    RoundState.NONE,
                    false)
                .phase())
        .isEqualTo(Phase.DEPLOY);
  }

  @Test
  void asksTheHumanToCheckTheStoppedSessionWhereTheAgentWentSilent() {
    Move move =
        Move.forTask(rules, TaskStatus.IN_PROGRESS, Facts.defaults(), RoundState.NONE, true);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.ask()).isEqualTo("check the stopped session");
  }

  @Test
  void asksTheHumanToAnswerTheSessionWhereItStoppedOnQuestion() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.IN_PROGRESS,
            Facts.defaults(),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.ask()).isEqualTo("answer the session");
  }

  @Test
  void asksTheHumanToReviewTheRoundWhereTheQuestionCameBackWithTheHandBack() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.ask()).isEqualTo("review the round");
  }

  @Test
  void asksNothingOfHumanWhileTheAgentIsWorking() {
    Move move =
        Move.forTask(rules, TaskStatus.IN_PROGRESS, Facts.defaults(), RoundState.NONE, false);

    assertThat(move.ask()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "REVIEW_PENDING", "CI_FAILED"})
  void putsAddRepoOnTheQuestionOfSessionAskingForRepository(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withWorkspace(Facts.Workspace.defaults().withRepoRequested(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
    assertThat(move.primary()).isEqualTo(TaskAction.ADD_REPO);
    assertThat(move.actions()).contains(TaskAction.ADD_REPO, TaskAction.FOCUS);
  }

  @Test
  void keepsFocusOnTheQuestionOfSessionAskingForNothingTheBoardCanAdd() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.IN_PROGRESS,
            Facts.defaults(),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    assertThat(move.actions()).doesNotContain(TaskAction.ADD_REPO);
  }

  @Test
  void offersNoAddRepoOnceTheSessionIsWorkingAgainWhateverRequestIsLeftOnTheTask() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.IN_PROGRESS,
            Facts.defaults().withWorkspace(Facts.Workspace.defaults().withRepoRequested(true)),
            RoundState.NONE,
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEWED", "APPROVED"})
  void offersTheCloseQuietlyOnceEveryRequestIsMerged(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults()
                .withDelivery(
                    Facts.Delivery.defaults().withHasReviewRequest(true).withRequestMerged(true)),
            RoundState.NONE,
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.attention()).isEqualTo(Attention.OPTIONAL);
    assertThat(move.ask()).isEqualTo("you can close the task");
    assertThat(move.primary()).isEqualTo(TaskAction.DONE);
  }

  @Test
  void advisesTheDeployOfTaskShippedWithoutRequestSinceNoReviewIsOpenOnIt() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults()
                .withDelivery(Facts.Delivery.defaults().withShippedWithoutRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.DEPLOY);
    assertThat(move.actions()).contains(TaskAction.DEPLOY);
  }

  @Test
  void offersNoDeployToTaskAtReviewPendingThatWasNeitherShippedNorRequested() {
    Move move =
        Move.forTask(rules, TaskStatus.REVIEW_PENDING, Facts.defaults(), RoundState.NONE, false);

    assertThat(move.actions()).doesNotContain(TaskAction.DEPLOY);
  }
}
