package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void clearsAPermissionTheSignStampedWhenWorkStartsAgain() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState asked = TaskReducer.signed(task, new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK), 1_000);

        TaskState started = TaskReducer.signed(asked, new SessionSign.WorkStarted(), 2_000);

        assertThat(asked.report()).isEqualTo(AgentReport.PERMISSION);
        assertThat(started.report()).isEqualTo(AgentReport.PLAIN);
    }

    @Test
    void keepsAPermissionTheSessionSaidItselfWhenWorkStartsAgain() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.PERMISSION).build();
        TaskState asked = TaskReducer.signed(task, new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK), 1_000);

        TaskState started = TaskReducer.signed(asked, new SessionSign.WorkStarted(), 2_000);

        assertThat(started.report()).isEqualTo(AgentReport.PERMISSION);
    }

    @Test
    void keepsAPermissionTheSessionReportedAfterAPromptStampedItWhenWorkStartsAgain() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState asked = TaskReducer.signed(task, new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK), 1_000);
        TaskState reported = TaskReducer.signed(asked, new SessionSign.Reported(), 2_000);

        TaskState started = TaskReducer.signed(reported, new SessionSign.WorkStarted(), 3_000);

        assertThat(started.report()).isEqualTo(AgentReport.PERMISSION);
    }

    @Test
    void leavesAQuestionTheSessionSaidWhenAPromptIsReported() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.QUESTION).build();

        TaskState asked = TaskReducer.signed(task, new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.SCREEN), 1_000);

        assertThat(asked.report()).isEqualTo(AgentReport.QUESTION);
    }

    @Test
    void keepsATurnFailureWhenAnIdleNoticeFollowsIt() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState failed = TaskReducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

        TaskState noticed = TaskReducer.signed(failed, new SessionSign.TurnEnded(null), 2_000);

        assertThat(SessionPhase.of(noticed.session())).isEqualTo(SessionPhase.FAILED);
        assertThat(noticed.session().halt().cause()).isEqualTo("overloaded");
    }

    @Test
    void endsATurnFailureWhenTheSessionRestarts() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState failed = TaskReducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

        TaskState restarted = TaskReducer.signed(failed, new SessionSign.Restarted("compact"), 2_000);

        assertThat(SessionPhase.of(restarted.session())).isEqualTo(SessionPhase.IDLE);
    }

    @Test
    void worksAgainWhenAFailedSessionStartsWork() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState failed = TaskReducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

        TaskState started = TaskReducer.signed(failed, new SessionSign.WorkStarted(), 2_000);

        assertThat(SessionPhase.of(started.session())).isEqualTo(SessionPhase.WORKING);
        assertThat(started.session().workStartedAt()).isEqualTo(2_000);
    }

    @Test
    void isGoneWithItsReasonWhenTheSessionEnds() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

        TaskState ended = TaskReducer.signed(task, new SessionSign.Ended("prompt_input_exit"), 1_000);

        assertThat(SessionPhase.of(ended.session())).isEqualTo(SessionPhase.GONE);
        assertThat(ended.session().halt().cause()).isEqualTo("prompt_input_exit");
    }

    @Test
    void keepsTheLogFileASessionNamedWithoutMovingItsPhase() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState idle = TaskReducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

        TaskState named = TaskReducer.signed(idle, new SessionSign.LogNamed("/logs/s.jsonl"), 2_000);

        assertThat(named.session().log()).isEqualTo("/logs/s.jsonl");
        assertThat(named.session().idleSince()).isEqualTo(1_000);
        assertThat(SessionPhase.of(named.session())).isEqualTo(SessionPhase.IDLE);
    }

    @Test
    void isIdleWhenATurnEndsWhileWorking() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState working = TaskReducer.signed(task, new SessionSign.WorkStarted(), 1_000);

        TaskState idle = TaskReducer.signed(working, new SessionSign.TurnEnded("/logs/s.jsonl"), 2_000);

        assertThat(SessionPhase.of(idle.session())).isEqualTo(SessionPhase.IDLE);
        assertThat(idle.session().log()).isEqualTo("/logs/s.jsonl");
    }

    @ParameterizedTest
    @ValueSource(longs = {500, 1_000})
    void keepsAHaltWhenACallIsNoNewerThanIt(long calledAt) {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState failed = TaskReducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

        TaskState called = TaskReducer.signed(failed, new SessionSign.Called(), calledAt);

        assertThat(SessionPhase.of(called.session())).isEqualTo(SessionPhase.FAILED);
    }

    @Test
    void dropsAHaltWhenACallComesAfterIt() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState ended = TaskReducer.signed(task, new SessionSign.Ended("prompt_input_exit"), 1_000);

        TaskState called = TaskReducer.signed(ended, new SessionSign.Called(), 2_000);

        assertThat(SessionPhase.of(called.session())).isEqualTo(SessionPhase.WORKING);
    }

    @Test
    void dropsAHaltWhenWorkStartsInTheSameMillisecond() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState failed = TaskReducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

        TaskState started = TaskReducer.signed(failed, new SessionSign.WorkStarted(), 1_000);

        assertThat(SessionPhase.of(started.session())).isEqualTo(SessionPhase.WORKING);
    }

    @Test
    void goesQuietSinceTheTurnEndedWhenAReadFindsNothingAfterItForLongerThanTheLimit() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState idle = TaskReducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

        TaskState quiet = TaskReducer.signed(idle, new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 5_000, null), 9_000);

        assertThat(SessionPhase.of(quiet.session())).isEqualTo(SessionPhase.QUIET);
        assertThat(quiet.session().quietSince()).isEqualTo(1_000);
    }

    @Test
    void aSessionStartedOverIsNotQuietFromTheOldTranscriptBeforeItsFirstHook() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState fresh = TaskReducer.sessionStartedOver(task);

        TaskState first = TaskReducer.signed(fresh, new SessionSign.Read(Session.Window.State.RUNNING, 0, 1_000, 0, 5_000, null), 9_000);
        TaskState second = TaskReducer.signed(first, new SessionSign.Read(Session.Window.State.RUNNING, 0, 1_000, 0, 5_000, null), 12_000);
        TaskState third = TaskReducer.signed(second, new SessionSign.Read(Session.Window.State.RUNNING, 0, 1_000, 0, 5_000, null), 14_000);

        assertThat(first.session().quietSince()).isZero();
        assertThat(second.session().quietSince()).isZero();
        assertThat(third.session().quietSince()).isEqualTo(9_000);
    }

    @Test
    void staysIdleWhenAReadFindsTheTurnEndWithinTheLimit() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState idle = TaskReducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

        TaskState read = TaskReducer.signed(idle, new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 5_000, null), 4_000);

        assertThat(SessionPhase.of(read.session())).isEqualTo(SessionPhase.IDLE);
    }

    @Test
    void goesQuietSinceTheWindowLastPrintedWhenItPrintedAfterTheTurnEnded() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState idle = TaskReducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

        TaskState quiet = TaskReducer.signed(idle, new SessionSign.Read(Session.Window.State.RUNNING, 2_000, 0, 0, 5_000, null), 9_000);

        assertThat(quiet.session().halt()).isNull();
        assertThat(quiet.session().quietSince()).isEqualTo(2_000);
    }

    @Test
    void endsAnEndedSessionWhenALaterReadFindsItsAgentProcessRunning() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState ended = TaskReducer.signed(task, new SessionSign.Ended("boom"), 1_000);

        TaskState read = TaskReducer.signed(ended, new SessionSign.Read(Session.Window.State.RUNNING, 8_000, 0, 0, 5_000, null), 9_000);

        assertThat(SessionPhase.of(read.session())).isNotEqualTo(SessionPhase.GONE);
    }

    @Test
    void endsAGoneSessionAReadSetWhenALaterReadFindsItsAgentProcessRunning() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState gone = TaskReducer.signed(task, new SessionSign.Read(Session.Window.State.MISSING, 0, 0, 0, 5_000, null), 2_000);

        TaskState read = TaskReducer.signed(gone, new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 5_000, null), 3_000);

        assertThat(SessionPhase.of(read.session())).isNotEqualTo(SessionPhase.GONE);
    }

    @Test
    void keepsAFailedSessionFailedWhenALaterReadFindsItsAgentProcessRunning() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState failed = TaskReducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

        TaskState read = TaskReducer.signed(failed, new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 5_000, null), 3_000);

        assertThat(SessionPhase.of(read.session())).isEqualTo(SessionPhase.FAILED);
    }

    @Test
    void endsAFailureWhenTheSessionsLogGrewAfterIt() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState failed = TaskReducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

        TaskState read = TaskReducer.signed(failed, new SessionSign.Read(Session.Window.State.UNREAD, 0, 2_000, 0, 5_000, null), 3_000);

        assertThat(SessionPhase.of(read.session())).isEqualTo(SessionPhase.WORKING);
    }

    @Test
    void endsTheSessionWithWhatItPrintedWhenAReadFindsItsWindowHoldingNoAgent() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

        TaskState read = TaskReducer.signed(task, new SessionSign.Read(Session.Window.State.DEAD_SHELL, 0, 0, 0, 5_000,
                "Error: bad config"), 9_000);

        assertThat(SessionPhase.of(read.session())).isEqualTo(SessionPhase.GONE);
        assertThat(read.session().halt().cause()).isEqualTo("Error: bad config");
    }

    @Test
    void keepsTheCauseAnEndedSessionGaveWhenAReadFindsItsWindowGone() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
        TaskState ended = TaskReducer.signed(task, new SessionSign.Ended("boom"), 8_000);

        TaskState read = TaskReducer.signed(ended, new SessionSign.Read(Session.Window.State.MISSING, 0, 0, 0, 5_000,
                null), 9_000);

        assertThat(read.session().halt()).isEqualTo(ended.session().halt());
    }
}
