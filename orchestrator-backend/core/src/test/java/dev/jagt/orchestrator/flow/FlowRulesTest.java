package dev.jagt.orchestrator.flow;

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
        assertThat(FlowRules.allows(status, TaskAction.SHIP, Facts.projected(false))).isTrue();
    }

    @Test
    void shipsAStuckTaskAgainOnlyOnceTheAgentThatWasPushingItIsGone() {
        assertThat(FlowRules.allows(TaskStatus.SHIPPING, TaskAction.SHIP, new Facts(false, () -> false))).isTrue();
        assertThat(FlowRules.allows(TaskStatus.SHIPPING, TaskAction.SHIP, new Facts(true, () -> true))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class,
            names = {"CI_POLLING", "CI_FAILED", "REVIEWED", "DEPLOYED", "REVERTED"})
    void shipsAFurtherRoundOnlyOntoARequestThatIsAlreadyOpen(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.SHIP, Facts.projected(true))).isTrue();
        assertThat(FlowRules.allows(status, TaskAction.SHIP, Facts.projected(false))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "DONE"})
    void refusesAShipForATaskWithNothingOnItsBranchYetOrNothingLeftToSay(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.SHIP, Facts.projected(true))).isFalse();
    }

    @Test
    void deploysAStalledDeployAgainWithNoRequestAtAll() {
        assertThat(FlowRules.allows(TaskStatus.DEPLOY_CONFLICT, TaskAction.DEPLOY, Facts.projected(false))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class,
            names = {"REVIEW_PENDING", "CI_POLLING", "CI_FAILED", "REVIEWED", "APPROVED", "DEPLOYED"})
    void landsAnOpenRequestWhateverTheReviewerSaidAboutIt(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.DEPLOY, Facts.projected(true))).isTrue();
        assertThat(FlowRules.allows(status, TaskAction.DEPLOY, Facts.projected(false))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "SHIPPING", "REVERTED", "DONE"})
    void refusesADeployWhereItCouldOnlyRaceTheAgentOrRefuse(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.DEPLOY, Facts.projected(true))).isFalse();
    }

    @Test
    void revertsATaskWhoseDeployActuallyLandedWhateverBecameOfItsRequest() {
        assertThat(FlowRules.allows(TaskStatus.DEPLOYED, TaskAction.REVERT, Facts.projected(true))).isTrue();
        assertThat(FlowRules.allows(TaskStatus.DEPLOYED, TaskAction.REVERT, Facts.projected(false))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DEPLOYED")
    void refusesARevertForATaskWithNothingLiveToTakeBackOut(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.REVERT, Facts.projected(true))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void readsAReviewRoundFromAnywhereButOnlyWhereThereIsARequestToRead(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.SWEEP, Facts.projected(true))).isTrue();
        assertThat(FlowRules.allows(status, TaskAction.SWEEP, Facts.projected(false))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void letsAHumanLookAtCloseOrRestartATaskWhereverItGotTo(TaskStatus status) {
        assertThat(FlowRules.allowed(status, Facts.projected(false))).contains(TaskAction.FOCUS, TaskAction.IDE,
                TaskAction.DIFF, TaskAction.RESPAWN, TaskAction.DONE);
    }

    @ParameterizedTest
    @CsvSource({"SHIP,OK,CI_POLLING", "SHIP,RELAYED,SHIPPING", "DEPLOY,OK,DEPLOYED",
            "DEPLOY,CONFLICT,DEPLOY_CONFLICT", "REVERT,OK,REVERTED", "REVERT,PARTIAL,DEPLOYED"})
    void movesTheTaskWhereTheOutcomeOfTheActionSays(TaskAction action, Outcome.Kind outcome, TaskStatus next) {
        assertThat(FlowRules.next(action, outcome)).contains(next);
    }

    @ParameterizedTest
    @CsvSource({"FOCUS,OK", "IDE,OK", "SHIP,CONFLICT", "REVERT,RELAYED"})
    void leavesTheTaskWhereItIsForAnOutcomeTheTableMapsNowhere(TaskAction action, Outcome.Kind outcome) {
        assertThat(FlowRules.next(action, outcome)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"NEW", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED", "DONE"})
    void acceptsTheStatusesATasksOwnAgentIsReportingAbout(TaskStatus status) {
        assertThat(FlowRules.reportable(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED", "DONE"})
    void refusesTheStatusesThatAreJagtsToSetRatherThanATasksToReport(TaskStatus status) {
        assertThat(FlowRules.reportable(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "SHIPPING")
    void doesNotProbeTheAgentForAStatusWhoseVerdictLivenessCannotChange(TaskStatus status) {
        AtomicBoolean probed = new AtomicBoolean();

        FlowRules.allowed(status, new Facts(true, () -> {
            probed.set(true);
            return true;
        }));

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
    @CsvSource({"DEPLOYED, REVIEWED", "DEPLOYED, APPROVED", "DEPLOY_CONFLICT, REVIEWED",
            "DEPLOY_CONFLICT, APPROVED", "DONE, REVIEWED", "DONE, APPROVED"})
    void keepsATaskWhoseCodeWentOutWhereItIsWhateverAReadOfTheRoundConcludes(TaskStatus from, TaskStatus verdict) {
        assertThat(FlowRules.reported(from, verdict)).isEqualTo(from);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "SHIPPING", "REVIEW_PENDING",
            "CI_POLLING", "CI_FAILED", "REVIEWED", "APPROVED", "DEPLOYED"})
    void landsEveryOtherTasksReportOnTheStatusItReported(TaskStatus from) {
        assertThat(FlowRules.reported(from, TaskStatus.REVIEW_PENDING)).isEqualTo(TaskStatus.REVIEW_PENDING);
    }
}
