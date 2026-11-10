package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskStatus;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class FlowRulesTest {

  private final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "REVIEW_PENDING"})
  void takesAShipAsTheHumansApprovalWhileTheWorkIsStillTheAgents(TaskStatus status) {
    assertThat(rules.allows(status, TaskAction.SHIP, Facts.defaults())).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"REVERTED,true", "DEPLOY_CONFLICT,true", "IN_PROGRESS,false"})
  void holdsItsLineAgainstAReportOnlyWhereTheStatusFactIsTheHumans(
      TaskStatus status, boolean held) {
    assertThat(FlowRules.holdsItsLine(status)).isEqualTo(held);
  }

  @ParameterizedTest
  @CsvSource({"NEW,true", "IN_PROGRESS,true", "REVIEW_PENDING,true", "DONE,false"})
  void offersScenariosOnATaskThatHasNotDeployedWhereAcceptanceIsReady(
      TaskStatus status, boolean offered) {
    Facts ready = Facts.defaults().withReach(Facts.Reach.defaults().withScenarios(true));

    assertThat(rules.allows(status, TaskAction.SCENARIOS, ready)).isEqualTo(offered);
  }

  @Test
  void offersNoScenariosWhereAcceptanceIsOffOrHoldsNoWorkspace() {
    assertThat(rules.allows(TaskStatus.IN_PROGRESS, TaskAction.SCENARIOS, Facts.defaults()))
        .isFalse();
  }

  @Test
  void offersNoScenariosOnATaskWhoseDeployIsLive() {
    Facts live =
        Facts.defaults()
            .withReach(Facts.Reach.defaults().withScenarios(true))
            .withDelivery(Facts.Delivery.defaults().withLiveDeploy(true));

    assertThat(rules.allows(TaskStatus.DEPLOYED, TaskAction.SCENARIOS, live)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"SHIPPING,true", "DEPLOY_CONFLICT,true", "IN_PROGRESS,false"})
  void awaitsAnInstructionOnlyWhereJagtRelayedOne(TaskStatus status, boolean awaits) {
    assertThat(FlowRules.awaitsItsInstruction(status)).isEqualTo(awaits);
  }

  @ParameterizedTest
  @CsvSource({"REVIEWED,true", "APPROVED,true", "DEPLOYED,true", "IN_PROGRESS,false"})
  void isMovedOnByAReviewerAloneOnlyWhenGreenWithNothingLeftForItsSession(
      TaskStatus status, boolean alone) {
    assertThat(FlowRules.movedOnByReviewerAlone(status)).isEqualTo(alone);
  }

  @Test
  void offersApprovePlanAtAPendingPlanAndAtNoOtherStatus() {
    Facts facts = Facts.defaults();

    assertThat(rules.allowed(TaskStatus.PLAN_PENDING, facts)).contains(TaskAction.APPROVE_PLAN);
    assertThat(
            java.util.Arrays.stream(TaskStatus.values())
                .filter(status -> status != TaskStatus.PLAN_PENDING)
                .filter(status -> rules.allows(status, TaskAction.APPROVE_PLAN, facts)))
        .isEmpty();
  }

  @Test
  void movesAnApprovedPlanBackToWorkOnceTheSessionIsTold() {
    assertThat(
            rules.next(TaskStatus.PLAN_PENDING, TaskAction.APPROVE_PLAN, Outcome.Kind.RELAYED))
        .contains(TaskStatus.IN_PROGRESS);
  }

  @Test
  void shipsAStuckTaskAgainOnlyOnceTheAgentThatWasPushingItIsGone() {
    assertThat(rules.allows(TaskStatus.SHIPPING, TaskAction.SHIP, Facts.defaults())).isTrue();
    assertThat(
            rules.allows(
                TaskStatus.SHIPPING,
                TaskAction.SHIP,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))
                    .withHold(Facts.Hold.defaults().withAgentLive(() -> true))))
        .isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"CI_POLLING", "CI_FAILED", "REVIEWED", "DEPLOYED", "REVERTED"})
  void shipsAFurtherRoundOnlyOntoARequestThatIsAlreadyOpen(TaskStatus status) {
    assertThat(
            rules.allows(
                status,
                TaskAction.SHIP,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))))
        .isTrue();
    assertThat(rules.allows(status, TaskAction.SHIP, Facts.defaults())).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"NEW", "DONE"})
  void refusesAShipForATaskWithNothingOnItsBranchYetOrNothingLeftToSay(TaskStatus status) {
    assertThat(
            rules.allows(
                status,
                TaskAction.SHIP,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))))
        .isFalse();
  }

  @Test
  void deploysAStalledDeployAgainWithNoRequestAtAll() {
    assertThat(rules.allows(TaskStatus.DEPLOY_CONFLICT, TaskAction.DEPLOY, Facts.defaults()))
        .isTrue();
  }

  @Test
  void refusesADeployWhileTheConflictIsARevertsAndStillOffersTheRevert() {
    Facts facts =
        Facts.defaults()
            .withDelivery(
                Facts.Delivery.defaults()
                    .withHasReviewRequest(true)
                    .withLiveDeploy(true)
                    .withRevertConflict(true));

    assertThat(rules.allows(TaskStatus.DEPLOY_CONFLICT, TaskAction.DEPLOY, facts)).isFalse();
    assertThat(rules.allows(TaskStatus.DEPLOY_CONFLICT, TaskAction.REVERT, facts)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEW_PENDING", "CI_POLLING", "CI_FAILED", "REVIEWED", "APPROVED", "DEPLOYED"})
  void landsAnOpenRequestWhateverTheReviewerSaidAboutIt(TaskStatus status) {
    assertThat(
            rules.allows(
                status,
                TaskAction.DEPLOY,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))))
        .isTrue();
    assertThat(rules.allows(status, TaskAction.DEPLOY, Facts.defaults())).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"NEW", "IN_PROGRESS", "SHIPPING", "REVERTED", "DONE"})
  void refusesADeployWhereItCouldOnlyRaceTheAgentOrRefuse(TaskStatus status) {
    assertThat(
            rules.allows(
                status,
                TaskAction.DEPLOY,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))))
        .isFalse();
  }

  @Test
  void revertsATaskWhoseDeployActuallyLandedWhateverBecameOfItsRequest() {
    assertThat(
            rules.allows(
                TaskStatus.DEPLOYED,
                TaskAction.REVERT,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))))
        .isTrue();
    assertThat(rules.allows(TaskStatus.DEPLOYED, TaskAction.REVERT, Facts.defaults())).isTrue();
  }

  @Test
  void revertsWhatADeployStoppedByAConflictLeftLive() {
    assertThat(rules.allows(TaskStatus.DEPLOY_CONFLICT, TaskAction.REVERT, Facts.defaults()))
        .isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"DEPLOYED", "DEPLOY_CONFLICT"})
  void refusesARevertForATaskWithNothingLiveToTakeBackOut(TaskStatus status) {
    assertThat(
            rules.allows(
                status,
                TaskAction.REVERT,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))))
        .isFalse();
  }

  @ParameterizedTest
  @EnumSource(TaskStatus.class)
  void readsAReviewRoundFromAnywhereButOnlyWhereThereIsARequestToRead(TaskStatus status) {
    assertThat(
            rules.allows(
                status,
                TaskAction.SWEEP,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))))
        .isTrue();
    assertThat(rules.allows(status, TaskAction.SWEEP, Facts.defaults())).isFalse();
  }

  @ParameterizedTest
  @EnumSource(TaskStatus.class)
  void letsAHumanLookAtCloseOrRestartATaskWhereverItGotTo(TaskStatus status) {
    assertThat(rules.allowed(status, Facts.defaults()))
        .contains(
            TaskAction.FOCUS, TaskAction.IDE, TaskAction.DIFF, TaskAction.RESPAWN, TaskAction.DONE);
  }

  @ParameterizedTest
  @CsvSource({
    "SHIP,OK,CI_POLLING",
    "SHIP,RELAYED,SHIPPING",
    "DEPLOY,OK,DEPLOYED",
    "DEPLOY,CONFLICT,DEPLOY_CONFLICT",
    "REVERT,OK,REVERTED",
    "REVERT,PARTIAL,DEPLOYED",
    "REVERT,CONFLICT,DEPLOY_CONFLICT"
  })
  void movesTheTaskWhereTheOutcomeOfTheActionSays(
      TaskAction action, Outcome.Kind outcome, TaskStatus next) {
    assertThat(rules.next(TaskStatus.REVIEWED, action, outcome)).contains(next);
  }

  @ParameterizedTest
  @CsvSource({"FOCUS,OK", "IDE,OK", "SHIP,CONFLICT", "REVERT,RELAYED"})
  void leavesTheTaskWhereItIsForAnOutcomeTheTableMapsNowhere(
      TaskAction action, Outcome.Kind outcome) {
    assertThat(rules.next(TaskStatus.REVIEWED, action, outcome)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"DEPLOYED", "DEPLOY_CONFLICT"})
  void letsARevertNameAMergeWhereTheDeployIsLive(TaskStatus status) {
    assertThat(rules.next(status, TaskAction.REVERT, Outcome.Kind.RELAYED)).contains(status);
  }

  @ParameterizedTest
  @CsvSource({"IN_PROGRESS", "CI_POLLING", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED"})
  void keepsTheTaskWhereItIsWhenItsGitCaseIsHandedToTheSession(TaskStatus status) {
    assertThat(rules.next(status, TaskAction.HAND_OFF, Outcome.Kind.RELAYED)).contains(status);
  }

  @Test
  void keepsTheConflictWaitingWhenARevertFromItTookOutOnlySomeOfTheDeploy() {
    assertThat(rules.next(TaskStatus.DEPLOY_CONFLICT, TaskAction.REVERT, Outcome.Kind.PARTIAL))
        .isEmpty();
  }

  @Test
  void refusesAPlanReportedByATaskThatHasAlreadyWrittenCode() {
    assertThat(rules.refusedReport(TaskStatus.REVIEW_PENDING, TaskStatus.PLAN_PENDING))
        .get()
        .asString()
        .contains("comes before the code");
  }

  @Test
  void refusesAPlanReportedFromVerifyingAndNamesTheWayBack() {
    assertThat(rules.refusedReport(TaskStatus.VERIFYING, TaskStatus.PLAN_PENDING))
        .get()
        .asString()
        .contains("report IN_PROGRESS first, then PLAN_PENDING with the new probes");
  }

  @Test
  void holdsAHandBackAtVerifyingWhileTheProjectStillOwesAVerificationRun() {
    assertThat(rules.reported(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING, true))
        .isEqualTo(TaskStatus.VERIFYING);
  }

  @Test
  void letsAHandBackReachTheHumanWhereNothingIsLeftToVerify() {
    assertThat(rules.reported(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING, false))
        .isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {
        "NEW",
        "VERIFYING",
        "REVIEWED",
        "APPROVED",
        "DEPLOYED",
        "DEPLOY_CONFLICT",
        "REVERTED",
        "DONE"
      })
  void acceptsTheStatusesATasksOwnAgentIsReportingAbout(TaskStatus status) {
    assertThat(FlowRules.reportable(status)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {
        "NEW",
        "VERIFYING",
        "REVIEWED",
        "APPROVED",
        "DEPLOYED",
        "DEPLOY_CONFLICT",
        "REVERTED",
        "DONE"
      })
  void refusesTheStatusesThatAreJagtsToSetRatherThanATasksToReport(TaskStatus status) {
    assertThat(FlowRules.reportable(status)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = TaskStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "SHIPPING")
  void doesNotProbeTheAgentForAStatusWhoseVerdictLivenessCannotChange(TaskStatus status) {
    AtomicBoolean probed = new AtomicBoolean();

    rules.allowed(
        status,
        Facts.defaults()
            .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))
            .withHold(
                Facts.Hold.defaults()
                    .withAgentLive(
                        () -> {
                          probed.set(true);
                          return true;
                        })));

    assertThat(probed).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEWED", "APPROVED", "DEPLOYED", "REVERTED"})
  void refusesToSayATaskIsWaitingOnChecksOnceTheReviewHasPassedIt(TaskStatus past) {
    assertThat(rules.reportable(past, TaskStatus.CI_POLLING)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"NEW", "IN_PROGRESS", "SHIPPING", "REVIEW_PENDING", "CI_POLLING", "CI_FAILED"})
  void acceptsTheRequestLinkFromATaskThatCouldStillBeWaitingOnIt(TaskStatus waiting) {
    assertThat(rules.reportable(waiting, TaskStatus.CI_POLLING)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "SHIPPING", "REVIEW_PENDING", "CI_FAILED"})
  void letsATaskSayWhatItIsDoingWhereverItGotTo(TaskStatus said) {
    assertThat(rules.reportable(TaskStatus.DEPLOYED, said)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "SHIPPING", "REVIEW_PENDING", "CI_FAILED"})
  void keepsARevertedDeployOnTheRecordWhateverItsAgentReports(TaskStatus said) {
    assertThat(rules.reportable(TaskStatus.REVERTED, said)).isTrue();
    assertThat(rules.reported(TaskStatus.REVERTED, said)).isEqualTo(TaskStatus.REVERTED);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "REVIEW_PENDING", "CI_FAILED"})
  void keepsADeployConflictUntilTheDeployFinishesWhateverItsAgentReports(TaskStatus said) {
    assertThat(rules.reported(TaskStatus.DEPLOY_CONFLICT, said))
        .isEqualTo(TaskStatus.DEPLOY_CONFLICT);
  }

  @ParameterizedTest
  @CsvSource({
    "DEPLOYED, REVIEWED",
    "DEPLOYED, APPROVED",
    "DEPLOY_CONFLICT, REVIEWED",
    "DEPLOY_CONFLICT, APPROVED",
    "DONE, REVIEWED",
    "DONE, APPROVED"
  })
  void keepsATaskWhoseCodeWentOutWhereItIsWhateverAReadOfTheRoundConcludes(
      TaskStatus from, TaskStatus verdict) {
    assertThat(rules.readLands(from, verdict)).isEqualTo(from);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {
        "NEW",
        "IN_PROGRESS",
        "SHIPPING",
        "REVIEW_PENDING",
        "CI_POLLING",
        "CI_FAILED",
        "REVIEWED",
        "APPROVED",
        "DEPLOYED"
      })
  void landsEveryOtherTasksReportOnTheStatusItReported(TaskStatus from) {
    assertThat(rules.reported(from, TaskStatus.REVIEW_PENDING))
        .isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"CI_POLLING", "REVIEWED", "APPROVED"})
  void stopsATaskWaitingOnTheHostWhenItsRunReadsRed(TaskStatus waiting) {
    assertThat(rules.readLands(waiting, TaskStatus.CI_FAILED)).isEqualTo(TaskStatus.CI_FAILED);
  }

  @ParameterizedTest
  @CsvSource({"REVIEWED", "APPROVED"})
  void movesAFailedTaskOnWhenItsRerunPipelineReadsGreen(TaskStatus verdict) {
    assertThat(rules.readLands(TaskStatus.CI_FAILED, verdict)).isEqualTo(verdict);
  }

  @ParameterizedTest
  @CsvSource({
    "NEW, REVIEWED",
    "IN_PROGRESS, REVIEWED",
    "IN_PROGRESS, APPROVED",
    "VERIFYING, APPROVED",
    "SHIPPING, REVIEWED",
    "PLAN_PENDING, REVIEWED",
    "REVIEW_PENDING, APPROVED"
  })
  void leavesATaskItsSessionWorksInWhereItIsWhenItsRunReadsGreen(
      TaskStatus busy, TaskStatus verdict) {
    assertThat(rules.readLands(busy, verdict)).isEqualTo(busy);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "REVIEW_PENDING", "SHIPPING", "DEPLOYED"})
  void leavesATaskSomeoneIsOnWhereItIsWhenItsRunReadsRed(TaskStatus busy) {
    assertThat(rules.readLands(busy, TaskStatus.CI_FAILED)).isEqualTo(busy);
  }

  @ParameterizedTest
  @CsvSource({"PLAN_PENDING,true", "REVIEW_PENDING,true", "VERIFYING,false"})
  void theMasterReadsAPlanOrAHandBackButNotARoundStillBeingVerified(
      TaskStatus status, boolean read) {
    assertThat(FlowRules.readByTheMaster(status)).isEqualTo(read);
  }

  @ParameterizedTest
  @CsvSource({"REVIEW_PENDING,SHIP", "APPROVED,DEPLOY", "DEPLOYED,REVERT"})
  void refusesGitWithTheHumansCredentialsOnceWhatItObeysChanged(
      TaskStatus status, TaskAction action) {
    assertThat(
            rules.allows(
                status,
                action,
                Facts.defaults()
                    .withDelivery(
                        Facts.Delivery.defaults().withHasReviewRequest(true).withLiveDeploy(true))
                    .withWorkspace(Facts.Workspace.defaults().withGitStateChanged(true))))
        .isFalse();
  }

  @Test
  void offersTrustOnlyOnceWhatGitObeysChanged() {
    assertThat(
            rules.allows(
                TaskStatus.APPROVED,
                TaskAction.TRUST,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))
                    .withWorkspace(Facts.Workspace.defaults().withGitStateChanged(true))))
        .isTrue();
    assertThat(
            rules.allows(
                TaskStatus.APPROVED,
                TaskAction.TRUST,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))))
        .isFalse();
  }

  @Test
  void parksATaskWhoseGitChangedWithTheHumanAndTrustAsTheMove() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.APPROVED,
            Facts.defaults()
                .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))
                .withWorkspace(Facts.Workspace.defaults().withGitStateChanged(true)),
            RoundState.NONE,
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
    assertThat(move.primary()).isEqualTo(TaskAction.TRUST);
  }

  @Test
  void offersLiftOnlyWhileJagtHoldsTheTaskStopped() {
    assertThat(
            rules.allows(
                TaskStatus.CI_POLLING,
                TaskAction.LIFT,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))
                    .withHold(Facts.Hold.defaults().withStopped(true))))
        .isTrue();
    assertThat(
            rules.allows(
                TaskStatus.CI_POLLING,
                TaskAction.LIFT,
                Facts.defaults()
                    .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))))
        .isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "NEW,false,false",
    "PLAN_PENDING,false,false",
    "IN_PROGRESS,false,true",
    "REVERTED,false,true",
    "DONE,false,false",
    "IN_PROGRESS,true,false"
  })
  void offersPauseOnlyToAStartedTaskNotAlreadyStopped(
      TaskStatus status, boolean stopped, boolean offered) {
    assertThat(
            rules.allows(
                status,
                TaskAction.PAUSE,
                Facts.defaults().withHold(Facts.Hold.defaults().withStopped(stopped))))
        .isEqualTo(offered);
  }

  @ParameterizedTest
  @CsvSource({"true,true,true", "true,false,false", "false,true,false", "false,false,false"})
  void offersEscalateOnlyOnAStopWithAModelTierAbove(
      boolean stopped, boolean tierAbove, boolean offered) {
    assertThat(
            rules.allows(
                TaskStatus.IN_PROGRESS,
                TaskAction.ESCALATE,
                Facts.defaults()
                    .withHold(
                        Facts.Hold.defaults().withStopped(stopped).withTierAbove(tierAbove))))
        .isEqualTo(offered);
  }

  @Test
  void parksAStoppedTaskWithTheHumanAndLiftAsTheMove() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.CI_POLLING,
            Facts.defaults()
                .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true))
                .withHold(Facts.Hold.defaults().withStopped(true)),
            RoundState.NONE,
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
    assertThat(move.primary()).isEqualTo(TaskAction.LIFT);
  }

  @ParameterizedTest
  @CsvSource({"true,false", "false,false", "true,true", "false,true"})
  void stopsATaskWhoseRoundReadsRedWhateverElseItSays(boolean unresolved, boolean approved) {
    assertThat(FlowRules.readReview(unresolved, approved, Pipeline.RED, RedCause.CODE, false))
        .contains(TaskStatus.CI_FAILED);
  }

  @ParameterizedTest
  @CsvSource({"true,false", "false,false", "true,true", "false,true"})
  void keepsATaskWhereItIsWhileARedRunThatDiedOnInfrastructureHasARetryLeft(
      boolean unresolved, boolean approved) {
    assertThat(
            FlowRules.readReview(unresolved, approved, Pipeline.RED, RedCause.INFRASTRUCTURE, true))
        .isEmpty();
  }

  @Test
  void stopsATaskWhoseRedRunDiedOnInfrastructureOnceNoRetryIsLeft() {
    assertThat(FlowRules.readReview(false, false, Pipeline.RED, RedCause.INFRASTRUCTURE, false))
        .contains(TaskStatus.CI_FAILED);
  }

  @ParameterizedTest
  @CsvSource({"GREEN,false", "GREEN,true", "RUNNING,true", "NONE,false"})
  void leavesATaskWhereItIsWhileAThreadIsStillOpen(Pipeline checks, boolean approved) {
    assertThat(FlowRules.readReview(true, approved, checks, RedCause.CODE, false)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"GREEN", "RUNNING", "UNKNOWN", "NONE"})
  void approvesARoundWithNothingOpenWhateverTheChecksSaidShortOfRed(Pipeline checks) {
    assertThat(FlowRules.readReview(false, true, checks, RedCause.CODE, false))
        .contains(TaskStatus.APPROVED);
  }

  @Test
  void reviewsACleanGreenRoundNobodyApprovedYet() {
    assertThat(FlowRules.readReview(false, false, Pipeline.GREEN, RedCause.CODE, false))
        .contains(TaskStatus.REVIEWED);
  }

  @ParameterizedTest
  @CsvSource({"RUNNING", "UNKNOWN", "NONE"})
  void leavesACleanRoundWhoseChecksAreNotGreenWhereItIs(Pipeline checks) {
    assertThat(FlowRules.readReview(false, false, checks, RedCause.CODE, false)).isEmpty();
  }

  @Test
  void handsAVerifiedRoundToTheHumanAndAFailedOneBackToItsSession() {
    assertThat(rules.verified(true)).isEqualTo(TaskStatus.REVIEW_PENDING);
    assertThat(rules.verified(false)).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void putsASessionHandedWorkBackAtWorkAndAResumedRequestOnItsChecks() {
    assertThat(FlowRules.relayed()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(FlowRules.resumedOnRequest()).isEqualTo(TaskStatus.CI_POLLING);
  }

  @ParameterizedTest
  @CsvSource({"DONE,true", "DEPLOYED,false", "REVERTED,false"})
  void saysATaskIsClosedOnlyOnceDone(TaskStatus status, boolean closed) {
    assertThat(FlowRules.closed(status)).isEqualTo(closed);
  }

  @ParameterizedTest
  @CsvSource({
    "NEW,true",
    "IN_PROGRESS,true",
    "SHIPPING,true",
    "REVIEW_PENDING,false",
    "CI_POLLING,false"
  })
  void watchesTheAgentOnlyWhereItIsExpectedToAct(TaskStatus status, boolean watched) {
    assertThat(FlowRules.watched(status)).isEqualTo(watched);
  }

  @ParameterizedTest
  @CsvSource({"REVIEW_PENDING,true", "CI_FAILED,true", "IN_PROGRESS,false", "CI_POLLING,false"})
  void handsBackOnlyWithAReviewPendingOrFailedChecks(TaskStatus status, boolean handsBack) {
    assertThat(FlowRules.handsBack(status)).isEqualTo(handsBack);
  }

  @ParameterizedTest
  @CsvSource({
    "VERIFYING,true",
    "PLAN_PENDING,true",
    "REVIEW_PENDING,true",
    "IN_PROGRESS,false",
    "CI_FAILED,false"
  })
  void headsAHandBackForTheMasterOnceItIsVerifyingOrWaitingToBeRead(
      TaskStatus status, boolean headed) {
    assertThat(FlowRules.headedForTheMaster(status)).isEqualTo(headed);
  }

  @ParameterizedTest
  @CsvSource({"REVIEWED,true", "APPROVED,true", "CI_POLLING,false", "DEPLOYED,false"})
  void letsTheMasterDeployOnlyARequestWaitingForOne(TaskStatus status, boolean deployed) {
    assertThat(FlowRules.deployedByTheMaster(status)).isEqualTo(deployed);
  }

  @ParameterizedTest
  @CsvSource({"DEPLOYED,true", "APPROVED,false"})
  void checksWhereItLandedOnlyOnceDeployed(TaskStatus status, boolean checked) {
    assertThat(FlowRules.checkedWhereItLanded(status)).isEqualTo(checked);
  }

  @ParameterizedTest
  @CsvSource({"PLAN_PENDING,true", "REVIEW_PENDING,false"})
  void holdsPlanOnlyWhilePlanPending(TaskStatus status, boolean holds) {
    assertThat(FlowRules.holdsPlan(status)).isEqualTo(holds);
  }

  @ParameterizedTest
  @CsvSource({"DEPLOY_CONFLICT,true", "DEPLOYED,false"})
  void conflictsInTheDeployWorktreeOnlyOnceDeployConflict(TaskStatus status, boolean conflicted) {
    assertThat(FlowRules.conflictedInTheDeployWorktree(status)).isEqualTo(conflicted);
  }

  @ParameterizedTest
  @CsvSource({"APPROVED,true,false", "REVIEWED,false,true", "CI_FAILED,false,false"})
  void tellsAnApprovedReadFromAReviewedOne(TaskStatus read, boolean approved, boolean reviewed) {
    assertThat(FlowRules.approved(read)).isEqualTo(approved);
    assertThat(FlowRules.reviewed(read)).isEqualTo(reviewed);
  }

  @Test
  void mentionsAnActionTheTableHasARuleFor() {
    assertThat(FlowRules.mentions(TaskAction.DEPLOY)).isTrue();
  }

  @Test
  void leadsToEveryStatusAnActionCanEndIn() {
    assertThat(rules.targets())
        .contains(TaskStatus.DEPLOYED, TaskStatus.DEPLOY_CONFLICT, TaskStatus.REVERTED);
  }

  @Test
  void readsOnlyTheStatusesAHostRoundCanConclude() {
    assertThat(FlowRules.reads())
        .containsExactlyInAnyOrder(TaskStatus.REVIEWED, TaskStatus.APPROVED, TaskStatus.CI_FAILED);
  }

  @Test
  void redirectsOnlyVerifying() {
    assertThat(rules.redirects()).containsExactly(TaskStatus.VERIFYING);
  }

  @ParameterizedTest
  @EnumSource(value = TaskStatus.class, names = "DONE", mode = EnumSource.Mode.EXCLUDE)
  void offersAddRepoOnlyWhileASessionsRequestForARepositoryStands(TaskStatus status) {
    assertThat(
            rules.allows(
                status,
                TaskAction.ADD_REPO,
                Facts.defaults().withWorkspace(Facts.Workspace.defaults().withRepoRequested(true))))
        .isTrue();
    assertThat(rules.allows(status, TaskAction.ADD_REPO, Facts.defaults())).isFalse();
  }

  @Test
  void offersNoAddRepoOnAClosedTask() {
    assertThat(
            rules.allows(
                TaskStatus.DONE,
                TaskAction.ADD_REPO,
                Facts.defaults().withWorkspace(Facts.Workspace.defaults().withRepoRequested(true))))
        .isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"NEW", "IN_PROGRESS", "REVIEW_PENDING", "CI_FAILED"})
  void putsATaskBackToWorkOnceItsSessionIsToldWhereTheNewRepositoryIs(TaskStatus status) {
    assertThat(rules.next(status, TaskAction.ADD_REPO, Outcome.Kind.RELAYED))
        .contains(TaskStatus.IN_PROGRESS);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"PLAN_PENDING", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED"})
  void leavesAStatusAHumanOwnsWhereItIsWhenARepositoryIsAdded(TaskStatus status) {
    assertThat(rules.next(status, TaskAction.ADD_REPO, Outcome.Kind.RELAYED)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"NEW", "DEPLOY_CONFLICT", "DONE"},
      mode = EnumSource.Mode.EXCLUDE)
  void offersRemoveRepoOnlyWhereTheTaskHoldsSeveralRepositories(TaskStatus status) {
    assertThat(
            rules.allows(
                status,
                TaskAction.REMOVE_REPO,
                Facts.defaults().withWorkspace(Facts.Workspace.defaults().withSeveralRepos(true))))
        .isTrue();
    assertThat(
            rules.allows(
                status,
                TaskAction.REMOVE_REPO,
                Facts.defaults().withWorkspace(Facts.Workspace.defaults().withSeveralRepos(false))))
        .isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"NEW", "DEPLOY_CONFLICT", "DONE"})
  void offersNoRemoveRepoWhereARepositoryCannotLeave(TaskStatus status) {
    assertThat(
            rules.allows(
                status,
                TaskAction.REMOVE_REPO,
                Facts.defaults().withWorkspace(Facts.Workspace.defaults().withSeveralRepos(true))))
        .isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "REVIEW_PENDING", "REVIEWED", "DEPLOYED"})
  void leavesTheStatusWhereItIsWhenARepositoryIsRemoved(TaskStatus status) {
    assertThat(rules.next(status, TaskAction.REMOVE_REPO, Outcome.Kind.RELAYED))
        .contains(status);
  }

  @Test
  void awaitsTheApprovalNotWhileTheAgentStillWorks() {
    assertThat(FlowRules.awaitsApproval(TaskStatus.IN_PROGRESS)).isFalse();
  }

  @Test
  void movesADeployReadOnlyOnARedTheCodeOwnsAndOnlyOnADeployedTask() {
    assertThat(rules.deployReadLands(TaskStatus.DEPLOYED, true))
        .isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(rules.deployReadLands(TaskStatus.DEPLOYED, false))
        .isEqualTo(TaskStatus.DEPLOYED);
    assertThat(rules.deployReadLands(TaskStatus.IN_PROGRESS, true))
        .isEqualTo(TaskStatus.IN_PROGRESS);
  }
}
