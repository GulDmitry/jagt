package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskReducerTest {

    @Test
    void forgetsTheDeployConflictOnceTheDeployLands() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
        TaskState opened = TaskReducer.deployStepped(task, new DeployStep.Opened(List.of(
                Deployment.Target.of("api", "dev"))), 1_000);
        TaskState conflicted = TaskReducer.deployStepped(opened, new DeployStep.Conflicted("api",
                Deployment.Cause.MERGE), 1_000);

        TaskState deployed = TaskReducer.reduce(conflicted, new TaskMove.Ran(TaskAction.DEPLOY, Outcome.Kind.OK),
                "deployed", true, 2_000);

        assertThat(deployed.deployment().targets().getFirst().conflict()).isNull();
    }

    @Test
    void recordsWhereADeployOverTwoReposStoodWhenTheSecondConflicts() {
        TaskState task = TaskState.builder("a", "/wt", TaskStatus.REVIEWED).build();
        TaskState opened = TaskReducer.deployStepped(task, new DeployStep.Opened(List.of(
                Deployment.Target.of("a", "dev"), Deployment.Target.of("b", "dev"))), 1_000);
        TaskState merged = TaskReducer.deployStepped(opened, new DeployStep.Merged("a", "cafe"), 1_100);
        TaskState pushed = TaskReducer.deployStepped(merged, new DeployStep.Pushed("a", 1_200), 1_200);

        TaskState conflicted = TaskReducer.deployStepped(pushed, new DeployStep.Conflicted("b", Deployment.Cause.MERGE), 1_300);

        assertThat(conflicted.deployment().at()).isEqualTo(1);
        assertThat(conflicted.deployment().targets().get(0).merge()).isEqualTo("cafe");
        assertThat(conflicted.deployment().targets().get(1).conflicted()).isTrue();
    }

    @Test
    void keepsWhatAnEarlierPressLeftLiveWhenTheNextPressOpens() {
        TaskState task = TaskState.builder("a", "/wt", TaskStatus.REVIEWED).build();
        TaskState first = TaskReducer.deployStepped(TaskReducer.deployStepped(task, new DeployStep.Opened(List.of(
                Deployment.Target.of("a", "dev"), Deployment.Target.of("b", "dev"))), 1_000),
                new DeployStep.Merged("a", "cafe"), 1_100);

        TaskState second = TaskReducer.deployStepped(first, new DeployStep.Opened(List.of(
                Deployment.Target.of("a", "dev"), Deployment.Target.of("b", "dev"))), 2_000);

        assertThat(second.deployment().targets()).extracting(Deployment.Target::merge).containsExactly("cafe", null);
    }

    @Test
    void forgetsTheMergeOfARepositoryOnceItsRevertCommitIsRecorded() {
        TaskState task = TaskState.builder("a", "/wt", TaskStatus.DEPLOYED).build();
        TaskState deployed = TaskReducer.deployStepped(TaskReducer.deployStepped(task, new DeployStep.Opened(List.of(
                Deployment.Target.of("a", "dev"))), 1_000), new DeployStep.Merged("a", "cafe"), 1_100);

        TaskState reverted = TaskReducer.deployStepped(deployed, new DeployStep.Reverted("a", "beef"), 1_200);

        assertThat(reverted.deployment().live()).isFalse();
        assertThat(reverted.deployment().targets().getFirst().revertCommit()).isEqualTo("beef");
    }

    @Test
    void marksTheRepositoryWhoseDeployWorktreePathAnotherHeldAsBlocked() {
        TaskState task = TaskState.builder("a", "/wt", TaskStatus.REVIEWED).build();
        TaskState opened = TaskReducer.deployStepped(task, new DeployStep.Opened(List.of(
                Deployment.Target.of("a", "dev"), Deployment.Target.of("b", "dev"))), 1_000);

        TaskState blocked = TaskReducer.deployStepped(opened, new DeployStep.Blocked("b"), 1_100);

        assertThat(blocked.deployment().targets()).extracting(Deployment.Target::isBlocked)
                .containsExactly(false, true);
    }

    @Test
    void endsAHostReadStopOnAReadThatDisprovesIt() {
        TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING)
                .stop(new TaskStop(TaskStop.Trigger.INFRA, 1_000, "runner died")).build();

        TaskState ended = TaskReducer.ended(stopped, TaskStop.Trigger.Ends.HOST_READ, "", 2_000);

        assertThat(ended.stop()).isNull();
    }

    @Test
    void keepsALiftStopOnAReadThatDisprovesAHostReadStop() {
        TaskStop stop = new TaskStop(TaskStop.Trigger.STALLED, 1_000, "rounds bring nothing new");
        TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).stop(stop).build();

        TaskState kept = TaskReducer.ended(stopped, TaskStop.Trigger.Ends.HOST_READ, "", 2_000);

        assertThat(kept.stop()).isEqualTo(stop);
    }

    @Test
    void keepsTheStopThatStandsWhenAnotherOneIsSet() {
        TaskStop standing = new TaskStop(TaskStop.Trigger.INFRA, 1_000, "runner died");
        TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.DEPLOYED).stop(standing).build();

        TaskState kept = TaskReducer.stop(stopped, new TaskStop(TaskStop.Trigger.PACE, 2_000, "too fast"), 2_000);

        assertThat(kept.stop()).isEqualTo(standing);
    }

    @Test
    void requiresThePlanWhenATaskOpensInPlanModeBeforeTheCode() {
        TaskState planned = TaskReducer.planAsked(TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING).build(), true);

        assertThat(planned.planGate()).isEqualTo(PlanGate.REQUIRED);
    }

    @Test
    void opensTheGateWhenTheHumanApprovesARequiredPlan() {
        TaskState required = TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING).planGate(PlanGate.REQUIRED).build();

        assertThat(TaskReducer.planApproved(required).planGate()).isEqualTo(PlanGate.OPEN);
    }

    @Test
    void keepsThePlanGateWhenATaskOpensInPlanModePastTheCode() {
        TaskState past = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).planGate(PlanGate.CLOSED).build();

        assertThat(TaskReducer.planAsked(past, true).planGate()).isEqualTo(PlanGate.CLOSED);
    }

    @Test
    void keepsARequiredPlanGateWhenTheSessionDeclaresNoPlan() {
        TaskState required = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).planGate(PlanGate.REQUIRED).build();

        assertThat(TaskReducer.noPlanDeclared(required).planGate()).isEqualTo(PlanGate.REQUIRED);
    }

    @Test
    void keepsTheDeployConflictWhereAHeldReportLeavesTheTaskInIt() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
        TaskState opened = TaskReducer.deployStepped(task, new DeployStep.Opened(List.of(
                Deployment.Target.of("api", "dev"))), 1_000);
        TaskState conflicted = TaskReducer.deployStepped(opened, new DeployStep.Conflicted("api",
                Deployment.Cause.MERGE), 1_000);

        TaskState held = TaskReducer.reduce(conflicted, new TaskMove.Reported(TaskStatus.IN_PROGRESS, false),
                "working", false, 2_000);

        assertThat(held.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
        assertThat(held.deployment().conflictedTarget()).isEqualTo(conflicted.deployment().conflictedTarget());
    }

    @Test
    void liftsAStopCountedOnARequestWhenAnotherRequestReplacesIt() {
        TaskState shipped = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build()
                .withMrUrl("https://host/mr/571", 1_000)
                .withStop(new TaskStop(TaskStop.Trigger.SHIPS, 2, "4 ships", "https://host/mr/571"))
                .withTrail(RoundTrail.none().withRound(new RoundTrail.Round(1, 4, "", "a", "")));

        TaskState reopened = TaskReducer.relinked(shipped.withMrUrl("https://host/mr/573", 3_000), 3_000);

        assertThat(reopened.stopped()).isFalse();
        assertThat(reopened.trailOrNone()).isEqualTo(RoundTrail.liftedAt(3_000));
    }

    @Test
    void keepsAStopWhenTheSameRequestIsWrittenAgain() {
        TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build()
                .withMrUrl("https://host/mr/571", 1_000)
                .withStop(new TaskStop(TaskStop.Trigger.SHIPS, 2, "4 ships", "https://host/mr/571"));

        TaskState again = TaskReducer.relinked(stopped.withMrUrl("https://host/mr/571", 3_000), 3_000);

        assertThat(again.stop()).isEqualTo(stopped.stop());
    }

    @Test
    void keepsAStopCountedOnNoRequestWhenARequestReplacesTheOneOpen() {
        TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build()
                .withMrUrl("https://host/mr/571", 1_000)
                .withStop(new TaskStop(TaskStop.Trigger.ROUNDS, 2, "verify sent 3 rounds running"));

        TaskState reopened = TaskReducer.relinked(stopped.withMrUrl("https://host/mr/573", 3_000), 3_000);

        assertThat(reopened.stop()).isEqualTo(stopped.stop());
    }

    @ParameterizedTest
    @EnumSource(value = TaskStop.Trigger.class, names = {"CHECKS", "UNCHANGED_FAILURE"})
    void liftsAPipelineStopWhenARequestReplacesTheOneItWasCountedOn(TaskStop.Trigger trigger) {
        TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build()
                .withMrUrl("https://host/mr/571", 1_000)
                .withStop(new TaskStop(trigger, 2, "evidence").countedOn(List.of("https://host/mr/571")));

        TaskState reopened = TaskReducer.relinked(stopped.withMrUrl("https://host/mr/573", 3_000), 3_000);

        assertThat(reopened.stopped()).isFalse();
    }
}
