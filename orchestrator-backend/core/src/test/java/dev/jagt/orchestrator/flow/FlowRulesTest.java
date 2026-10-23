package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class FlowRulesTest {

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"IN_PROGRESS", "REVIEW_PENDING"})
    void takesAShipAsTheHumansApprovalWhileTheWorkIsStillTheAgents(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.SHIP, new Facts(false, false, () -> false, false, false, false))).isTrue();
    }

    @Test
    void offersApprovePlanAtAPendingPlanAndAtNoOtherStatus() {
        Facts facts = new Facts(false, false, () -> false, false, false, false);

        assertThat(FlowRules.allowed(TaskStatus.PLAN_PENDING, facts)).contains(TaskAction.APPROVE_PLAN);
        assertThat(java.util.Arrays.stream(TaskStatus.values()).filter(status -> status != TaskStatus.PLAN_PENDING)
                .filter(status -> FlowRules.allows(status, TaskAction.APPROVE_PLAN, facts))).isEmpty();
    }

    @Test
    void movesAnApprovedPlanBackToWorkOnceTheSessionIsTold() {
        assertThat(FlowRules.next(TaskStatus.PLAN_PENDING, TaskAction.APPROVE_PLAN, Outcome.Kind.RELAYED))
                .contains(TaskStatus.IN_PROGRESS);
    }

    @Test
    void shipsAStuckTaskAgainOnlyOnceTheAgentThatWasPushingItIsGone() {
        assertThat(FlowRules.allows(TaskStatus.SHIPPING, TaskAction.SHIP, new Facts(false, false,
                () -> false, false, false, false))).isTrue();
        assertThat(FlowRules.allows(TaskStatus.SHIPPING, TaskAction.SHIP, new Facts(true, false,
                () -> true, false, false, false))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class,
            names = {"CI_POLLING", "CI_FAILED", "REVIEWED", "DEPLOYED", "REVERTED"})
    void shipsAFurtherRoundOnlyOntoARequestThatIsAlreadyOpen(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.SHIP, new Facts(true, false, () -> false, false, false, false))).isTrue();
        assertThat(FlowRules.allows(status, TaskAction.SHIP, new Facts(false, false, () -> false, false, false, false))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "DONE"})
    void refusesAShipForATaskWithNothingOnItsBranchYetOrNothingLeftToSay(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.SHIP, new Facts(true, false, () -> false, false, false, false))).isFalse();
    }

    @Test
    void deploysAStalledDeployAgainWithNoRequestAtAll() {
        assertThat(FlowRules.allows(TaskStatus.DEPLOY_CONFLICT, TaskAction.DEPLOY, new Facts(false, false,
                () -> false, false, false, false))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class,
            names = {"REVIEW_PENDING", "CI_POLLING", "CI_FAILED", "REVIEWED", "APPROVED", "DEPLOYED"})
    void landsAnOpenRequestWhateverTheReviewerSaidAboutIt(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.DEPLOY, new Facts(true, false, () -> false, false, false, false))).isTrue();
        assertThat(FlowRules.allows(status, TaskAction.DEPLOY, new Facts(false, false, () -> false, false, false, false))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "SHIPPING", "REVERTED", "DONE"})
    void refusesADeployWhereItCouldOnlyRaceTheAgentOrRefuse(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.DEPLOY, new Facts(true, false, () -> false, false, false, false))).isFalse();
    }

    @Test
    void revertsATaskWhoseDeployActuallyLandedWhateverBecameOfItsRequest() {
        assertThat(FlowRules.allows(TaskStatus.DEPLOYED, TaskAction.REVERT, new Facts(true, false,
                () -> false, false, false, false))).isTrue();
        assertThat(FlowRules.allows(TaskStatus.DEPLOYED, TaskAction.REVERT, new Facts(false, false,
                () -> false, false, false, false))).isTrue();
    }

    @Test
    void revertsWhatADeployStoppedByAConflictLeftLive() {
        assertThat(FlowRules.allows(TaskStatus.DEPLOY_CONFLICT, TaskAction.REVERT, new Facts(false, false,
                () -> false, false, false, false))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"DEPLOYED", "DEPLOY_CONFLICT"})
    void refusesARevertForATaskWithNothingLiveToTakeBackOut(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.REVERT, new Facts(true, false, () -> false, false, false, false))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void readsAReviewRoundFromAnywhereButOnlyWhereThereIsARequestToRead(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.SWEEP, new Facts(true, false, () -> false, false, false, false))).isTrue();
        assertThat(FlowRules.allows(status, TaskAction.SWEEP, new Facts(false, false, () -> false, false, false, false))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void letsAHumanLookAtCloseOrRestartATaskWhereverItGotTo(TaskStatus status) {
        assertThat(FlowRules.allowed(status, new Facts(false, false, () -> false, false, false, false))).contains(TaskAction.FOCUS,
                TaskAction.IDE,
                TaskAction.DIFF, TaskAction.RESPAWN, TaskAction.DONE);
    }

    @ParameterizedTest
    @CsvSource({"SHIP,OK,CI_POLLING", "SHIP,RELAYED,SHIPPING", "DEPLOY,OK,DEPLOYED",
            "DEPLOY,CONFLICT,DEPLOY_CONFLICT", "REVERT,OK,REVERTED", "REVERT,PARTIAL,DEPLOYED"})
    void movesTheTaskWhereTheOutcomeOfTheActionSays(TaskAction action, Outcome.Kind outcome, TaskStatus next) {
        assertThat(FlowRules.next(TaskStatus.REVIEWED, action, outcome)).contains(next);
    }

    @ParameterizedTest
    @CsvSource({"FOCUS,OK", "IDE,OK", "SHIP,CONFLICT", "REVERT,RELAYED"})
    void leavesTheTaskWhereItIsForAnOutcomeTheTableMapsNowhere(TaskAction action, Outcome.Kind outcome) {
        assertThat(FlowRules.next(TaskStatus.REVIEWED, action, outcome)).isEmpty();
    }

    @Test
    void keepsTheConflictWaitingWhenARevertFromItTookOutOnlySomeOfTheDeploy() {
        assertThat(FlowRules.next(TaskStatus.DEPLOY_CONFLICT, TaskAction.REVERT, Outcome.Kind.PARTIAL)).isEmpty();
    }

    @Test
    void refusesAPlanReportedByATaskThatHasAlreadyWrittenCode() {
        assertThat(FlowRules.refusedReport(TaskStatus.REVIEW_PENDING, TaskStatus.PLAN_PENDING))
                .get().asString().contains("comes before the code");
    }

    @Test
    void holdsAHandBackAtVerifyingWhileTheProjectStillOwesAVerificationRun() {
        assertThat(FlowRules.reported(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING, true))
                .isEqualTo(TaskStatus.VERIFYING);
    }

    @Test
    void letsAHandBackReachTheHumanWhereNothingIsLeftToVerify() {
        assertThat(FlowRules.reported(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING, false))
                .isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"NEW", "VERIFYING", "REVIEWED", "APPROVED", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED", "DONE"})
    void acceptsTheStatusesATasksOwnAgentIsReportingAbout(TaskStatus status) {
        assertThat(FlowRules.reportable(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class,
            names = {"NEW", "VERIFYING", "REVIEWED", "APPROVED", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED", "DONE"})
    void refusesTheStatusesThatAreJagtsToSetRatherThanATasksToReport(TaskStatus status) {
        assertThat(FlowRules.reportable(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "SHIPPING")
    void doesNotProbeTheAgentForAStatusWhoseVerdictLivenessCannotChange(TaskStatus status) {
        AtomicBoolean probed = new AtomicBoolean();

        FlowRules.allowed(status, new Facts(true, false, () -> {
            probed.set(true);
            return true;
        }, false, false, false));

        assertThat(probed).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEWED", "APPROVED", "DEPLOYED", "REVERTED"})
    void refusesToSayATaskIsWaitingOnChecksOnceTheReviewHasPassedIt(TaskStatus past) {
        assertThat(FlowRules.reportable(past, TaskStatus.CI_POLLING)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "SHIPPING", "REVIEW_PENDING",
            "CI_POLLING", "CI_FAILED"})
    void acceptsTheRequestLinkFromATaskThatCouldStillBeWaitingOnIt(TaskStatus waiting) {
        assertThat(FlowRules.reportable(waiting, TaskStatus.CI_POLLING)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"IN_PROGRESS", "SHIPPING", "REVIEW_PENDING", "CI_FAILED"})
    void letsATaskSayWhatItIsDoingWhereverItGotTo(TaskStatus said) {
        assertThat(FlowRules.reportable(TaskStatus.DEPLOYED, said)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"IN_PROGRESS", "SHIPPING", "REVIEW_PENDING", "CI_FAILED"})
    void keepsARevertedDeployOnTheRecordWhateverItsAgentReports(TaskStatus said) {
        assertThat(FlowRules.reportable(TaskStatus.REVERTED, said)).isTrue();
        assertThat(FlowRules.reported(TaskStatus.REVERTED, said)).isEqualTo(TaskStatus.REVERTED);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"IN_PROGRESS", "REVIEW_PENDING", "CI_FAILED"})
    void keepsADeployConflictUntilTheDeployFinishesWhateverItsAgentReports(TaskStatus said) {
        assertThat(FlowRules.reported(TaskStatus.DEPLOY_CONFLICT, said)).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    }

    @ParameterizedTest
    @CsvSource({"DEPLOYED, REVIEWED", "DEPLOYED, APPROVED", "DEPLOY_CONFLICT, REVIEWED",
            "DEPLOY_CONFLICT, APPROVED", "DONE, REVIEWED", "DONE, APPROVED"})
    void keepsATaskWhoseCodeWentOutWhereItIsWhateverAReadOfTheRoundConcludes(TaskStatus from, TaskStatus verdict) {
        assertThat(FlowRules.readLands(from, verdict)).isEqualTo(from);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "SHIPPING", "REVIEW_PENDING",
            "CI_POLLING", "CI_FAILED", "REVIEWED", "APPROVED", "DEPLOYED"})
    void landsEveryOtherTasksReportOnTheStatusItReported(TaskStatus from) {
        assertThat(FlowRules.reported(from, TaskStatus.REVIEW_PENDING)).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"CI_POLLING", "REVIEWED", "APPROVED"})
    void stopsATaskWaitingOnTheHostWhenItsRunReadsRed(TaskStatus waiting) {
        assertThat(FlowRules.readLands(waiting, TaskStatus.CI_FAILED)).isEqualTo(TaskStatus.CI_FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"IN_PROGRESS", "REVIEW_PENDING", "SHIPPING", "DEPLOYED"})
    void leavesATaskSomeoneIsOnWhereItIsWhenItsRunReadsRed(TaskStatus busy) {
        assertThat(FlowRules.readLands(busy, TaskStatus.CI_FAILED)).isEqualTo(busy);
    }

    @ParameterizedTest
    @CsvSource({"PLAN_PENDING,true", "REVIEW_PENDING,true", "VERIFYING,false"})
    void theMasterReadsAPlanOrAHandBackButNotARoundStillBeingVerified(TaskStatus status, boolean read) {
        assertThat(FlowRules.readByTheMaster(status)).isEqualTo(read);
    }

    @ParameterizedTest
    @CsvSource({"REVIEW_PENDING,SHIP", "APPROVED,DEPLOY", "DEPLOYED,REVERT"})
    void refusesGitWithTheHumansCredentialsOnceWhatItObeysChanged(TaskStatus status, TaskAction action) {
        assertThat(FlowRules.allows(status, action, new Facts(true, true, () -> false, true, false, false))).isFalse();
    }

    @Test
    void offersTrustOnlyOnceWhatGitObeysChanged() {
        assertThat(FlowRules.allows(TaskStatus.APPROVED, TaskAction.TRUST, new Facts(true, false, () -> false, true, false, false)))
                .isTrue();
        assertThat(FlowRules.allows(TaskStatus.APPROVED, TaskAction.TRUST, new Facts(true, false, () -> false, false, false, false)))
                .isFalse();
    }

    @Test
    void parksATaskWhoseGitChangedWithTheHumanAndTrustAsTheMove() {
        Move move = Move.forTask(TaskStatus.APPROVED, new Facts(true, false, () -> false, true, false, false), RoundState.NONE,
                false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
        assertThat(move.primary()).isEqualTo(TaskAction.TRUST);
    }

    @Test
    void offersLiftOnlyWhileJagtHoldsTheTaskStopped() {
        assertThat(FlowRules.allows(TaskStatus.CI_POLLING, TaskAction.LIFT, new Facts(true, false, () -> false, false, true, false)))
                .isTrue();
        assertThat(FlowRules.allows(TaskStatus.CI_POLLING, TaskAction.LIFT, new Facts(true, false, () -> false, false, false, false)))
                .isFalse();
    }

    @Test
    void parksAStoppedTaskWithTheHumanAndLiftAsTheMove() {
        Move move = Move.forTask(TaskStatus.CI_POLLING, new Facts(true, false, () -> false, false, true, false), RoundState.NONE,
                false);

        assertThat(move.owner()).isEqualTo(Owner.YOU);
        assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
        assertThat(move.primary()).isEqualTo(TaskAction.LIFT);
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,false", "true,true", "false,true"})
    void stopsATaskWhoseRoundReadsRedWhateverElseItSays(boolean unresolved, boolean approved) {
        assertThat(FlowRules.readReview(unresolved, approved, Pipeline.RED)).contains(TaskStatus.CI_FAILED);
    }

    @ParameterizedTest
    @CsvSource({"GREEN,false", "GREEN,true", "RUNNING,true", "NONE,false"})
    void leavesATaskWhereItIsWhileAThreadIsStillOpen(Pipeline checks, boolean approved) {
        assertThat(FlowRules.readReview(true, approved, checks)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"GREEN", "RUNNING", "UNKNOWN", "NONE"})
    void approvesARoundWithNothingOpenWhateverTheChecksSaidShortOfRed(Pipeline checks) {
        assertThat(FlowRules.readReview(false, true, checks)).contains(TaskStatus.APPROVED);
    }

    @Test
    void reviewsACleanGreenRoundNobodyApprovedYet() {
        assertThat(FlowRules.readReview(false, false, Pipeline.GREEN)).contains(TaskStatus.REVIEWED);
    }

    @ParameterizedTest
    @CsvSource({"RUNNING", "UNKNOWN", "NONE"})
    void leavesACleanRoundWhoseChecksAreNotGreenWhereItIs(Pipeline checks) {
        assertThat(FlowRules.readReview(false, false, checks)).isEmpty();
    }

    @Test
    void handsAVerifiedRoundToTheHumanAndAFailedOneBackToItsSession() {
        assertThat(FlowRules.verified(true)).isEqualTo(TaskStatus.REVIEW_PENDING);
        assertThat(FlowRules.verified(false)).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void putsASessionHandedWorkBackAtWorkAndAResumedRequestOnItsChecks() {
        assertThat(FlowRules.relayed()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(FlowRules.resumedOnARequest()).isEqualTo(TaskStatus.CI_POLLING);
    }

    @ParameterizedTest
    @CsvSource({"IN_PROGRESS,true", "NEW,false", "VERIFYING,false"})
    void saysASessionIsAtWorkOnlyInProgress(TaskStatus status, boolean atWork) {
        assertThat(FlowRules.atWork(status)).isEqualTo(atWork);
    }

    @ParameterizedTest
    @CsvSource({"DONE,true", "DEPLOYED,false", "REVERTED,false"})
    void saysATaskIsClosedOnlyOnceDone(TaskStatus status, boolean closed) {
        assertThat(FlowRules.closed(status)).isEqualTo(closed);
    }

    @ParameterizedTest
    @CsvSource({"NEW,true", "IN_PROGRESS,true", "SHIPPING,true", "REVIEW_PENDING,false", "CI_POLLING,false"})
    void watchesTheAgentOnlyWhereItIsExpectedToAct(TaskStatus status, boolean watched) {
        assertThat(FlowRules.watched(status)).isEqualTo(watched);
    }

    @ParameterizedTest
    @CsvSource({"REVIEW_PENDING,true", "CI_FAILED,true", "IN_PROGRESS,false", "CI_POLLING,false"})
    void handsBackOnlyWithAReviewPendingOrFailedChecks(TaskStatus status, boolean handsBack) {
        assertThat(FlowRules.handsBack(status)).isEqualTo(handsBack);
    }

    @ParameterizedTest
    @CsvSource({"VERIFYING,true", "PLAN_PENDING,true", "REVIEW_PENDING,true", "IN_PROGRESS,false", "CI_FAILED,false"})
    void headsAHandBackForTheMasterOnceItIsVerifyingOrWaitingToBeRead(TaskStatus status, boolean headed) {
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
    void holdsAPlanOnlyWhilePlanPending(TaskStatus status, boolean holds) {
        assertThat(FlowRules.holdsAPlan(status)).isEqualTo(holds);
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
        assertThat(FlowRules.targets()).contains(TaskStatus.DEPLOYED, TaskStatus.DEPLOY_CONFLICT,
                TaskStatus.REVERTED);
    }

    @Test
    void readsOnlyTheStatusesAHostRoundCanConclude() {
        assertThat(FlowRules.reads()).containsExactlyInAnyOrder(TaskStatus.REVIEWED, TaskStatus.APPROVED,
                TaskStatus.CI_FAILED);
    }

    @Test
    void redirectsOnlyVerifying() {
        assertThat(FlowRules.redirects()).containsExactly(TaskStatus.VERIFYING);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = "DONE", mode = EnumSource.Mode.EXCLUDE)
    void offersAddRepoOnlyWhileASessionsRequestForARepositoryStands(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.ADD_REPO,
                new Facts(false, false, () -> false, false, false, true))).isTrue();
        assertThat(FlowRules.allows(status, TaskAction.ADD_REPO,
                new Facts(false, false, () -> false, false, false, false))).isFalse();
    }

    @Test
    void offersNoAddRepoOnAClosedTask() {
        assertThat(FlowRules.allows(TaskStatus.DONE, TaskAction.ADD_REPO,
                new Facts(false, false, () -> false, false, false, true))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "REVIEW_PENDING", "CI_FAILED"})
    void putsATaskBackToWorkOnceItsSessionIsToldWhereTheNewRepositoryIs(TaskStatus status) {
        assertThat(FlowRules.next(status, TaskAction.ADD_REPO, Outcome.Kind.RELAYED))
                .contains(TaskStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"PLAN_PENDING", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED"})
    void leavesAStatusAHumanOwnsWhereItIsWhenARepositoryIsAdded(TaskStatus status) {
        assertThat(FlowRules.next(status, TaskAction.ADD_REPO, Outcome.Kind.RELAYED)).isEmpty();
    }
}
