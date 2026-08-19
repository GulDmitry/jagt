package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskStatus;

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

    /**
     * A dead agent is what makes a task stuck at SHIPPING, so shipping again is the recovery; while the agent is
     * alive the push it was asked for is still in flight and a second one would race it.
     */
    @Test
    void shipsAStuckTaskAgainOnlyOnceTheAgentThatWasPushingItIsGone() {
        assertThat(FlowRules.allows(TaskStatus.SHIPPING, TaskAction.SHIP, new Facts(false, () -> false))).isTrue();
        assertThat(FlowRules.allows(TaskStatus.SHIPPING, TaskAction.SHIP, new Facts(true, () -> true))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"CI_POLLING", "CI_FAILED", "DEPLOYED", "REVERTED"})
    void shipsAFurtherRoundOnlyOntoARequestThatIsAlreadyOpen(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.SHIP, Facts.projected(true))).isTrue();
        assertThat(FlowRules.allows(status, TaskAction.SHIP, Facts.projected(false))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "DONE"})
    void refusesAShipForATaskWithNothingOnItsBranchYetOrNothingLeftToSay(TaskStatus status) {
        assertThat(FlowRules.allows(status, TaskAction.SHIP, Facts.projected(true))).isFalse();
    }

    /** A stalled deploy is finished by deploying again, whether or not a request was ever read. */
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

    /**
     * Nothing on the branch yet, a push in flight, an agent committing into the branch the deploy would merge, or
     * a revert that leaves the deploy branch already holding everything.
     */
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

    /** A task must not be able to talk itself onto a shared branch, out of one, or closed. */
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED", "DONE"})
    void refusesTheStatusesThatAreJagtsToSetRatherThanATasksToReport(TaskStatus status) {
        assertThat(FlowRules.reportable(status)).isFalse();
    }

    /** Answering it costs a process probe, so it is asked only where the answer can change the verdict. */
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
}
