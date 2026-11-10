package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.ModelTier;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.ToldOnce;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class TaskReducerTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @Test
  void keepsTheSilenceToldWhereTheAutoReviewPingsAreForgotten() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState told =
        reducer.told(
            reducer.told(task, ToldOnce.Kind.AUTO_REVIEW_WINDOW, "5"), ToldOnce.Kind.SILENCE, "9");

    TaskState forgot =
        reducer.untold(
            told, ToldOnce.Kind.AUTO_REVIEW_WINDOW, ToldOnce.Kind.AUTO_REVIEW_UNREADABLE);

    assertThat(forgot.toldOrNone().entries()).containsOnlyKeys(ToldOnce.Kind.SILENCE);
  }

  @Test
  void forgetsTheDeployConflictOnceTheDeployLands() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
    TaskState opened =
        reducer.deployStepped(
            task, new DeployStep.Opened(List.of(Deployment.Target.of("api", "dev"))), 1_000);
    TaskState conflicted =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("api", Deployment.Cause.MERGE), 1_000);

    TaskState deployed =
        reducer.reduce(
            conflicted,
            new TaskMove.Ran(TaskAction.DEPLOY, Outcome.Kind.OK),
            "deployed",
            true,
            2_000);

    assertThat(
            Objects.requireNonNull(deployed.deployment())
                .targets()
                .getFirst()
                .progress()
                .conflict())
        .isNull();
  }

  @Test
  void turnsStalledConflictIntoTheSessionsQuestionAndRestartsItsClock() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
    TaskState opened =
        reducer.deployStepped(
            task, new DeployStep.Opened(List.of(Deployment.Target.of("api", "dev"))), 1_000);
    TaskState conflicted =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("api", Deployment.Cause.MERGE), 1_000);

    TaskState referred = reducer.conflictReferred(conflicted, "stalled for 20 min", 2_000);

    assertThat(referred.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(referred.report()).isEqualTo(AgentReport.QUESTION);
    assertThat(referred.message()).isEqualTo("stalled for 20 min");
    assertThat(referred.currentConflict().orElseThrow().askedTheMaster()).isTrue();
    assertThat(referred.currentConflict().orElseThrow().movedAt()).isEqualTo(2_000);
    assertThat(Move.asking(referred.status(), referred.report())).isTrue();
  }

  @Test
  void asksTheMasterOfStalledConflictOnlyOnce() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
    TaskState opened =
        reducer.deployStepped(
            task, new DeployStep.Opened(List.of(Deployment.Target.of("api", "dev"))), 1_000);
    TaskState conflicted =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("api", Deployment.Cause.MERGE), 1_000);
    TaskState referred = reducer.conflictReferred(conflicted, "first", 2_000);

    TaskState again = reducer.conflictReferred(referred, "second", 3_000);

    assertThat(again.message()).isEqualTo("first");
    assertThat(again.currentConflict().orElseThrow().movedAt()).isEqualTo(2_000);
  }

  @Test
  void asksNothingWhereNoConflictStands() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.DEPLOYED).build();

    TaskState referred = reducer.conflictReferred(task, "stalled", 2_000);

    assertThat(referred.report()).isEqualTo(AgentReport.PLAIN);
  }

  @Test
  void recordsThatTheDeployPipelineOfLandedCommitWasReRun() {
    TaskState task = TaskState.builder("a", "/wt", TaskStatus.DEPLOYED).build();
    TaskState landed =
        reducer.deployStepped(
            reducer.deployStepped(
                task, new DeployStep.Opened(List.of(Deployment.Target.of("a", "dev"))), 1_000),
            new DeployStep.Merged("a", "cafe"),
            1_100);

    TaskState retried = reducer.deployStepped(landed, new DeployStep.Retried("a", "cafe"), 1_200);

    assertThat(Objects.requireNonNull(retried.deployment()).targets().getFirst().retriedTip())
        .isTrue();
  }

  @Test
  void recordsWhereDeployOverTwoReposStoodWhenTheSecondConflicts() {
    TaskState task = TaskState.builder("a", "/wt", TaskStatus.REVIEWED).build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("a", "dev"), Deployment.Target.of("b", "dev"))),
            1_000);
    TaskState merged = reducer.deployStepped(opened, new DeployStep.Merged("a", "cafe"), 1_100);
    TaskState pushed = reducer.deployStepped(merged, new DeployStep.Pushed("a", 1_200), 1_200);

    TaskState conflicted =
        reducer.deployStepped(
            pushed, new DeployStep.Conflicted("b", Deployment.Cause.MERGE), 1_300);

    assertThat(Objects.requireNonNull(conflicted.deployment()).at()).isEqualTo(1);
    assertThat(conflicted.deployment().targets().get(0).landing().merge()).isEqualTo("cafe");
    assertThat(conflicted.deployment().targets().get(1).conflicted()).isTrue();
  }

  @Test
  void keepsWhatAnEarlierPressLeftLiveWhenTheNextPressOpens() {
    TaskState task = TaskState.builder("a", "/wt", TaskStatus.REVIEWED).build();
    TaskState first =
        reducer.deployStepped(
            reducer.deployStepped(
                task,
                new DeployStep.Opened(
                    List.of(Deployment.Target.of("a", "dev"), Deployment.Target.of("b", "dev"))),
                1_000),
            new DeployStep.Merged("a", "cafe"),
            1_100);

    TaskState second =
        reducer.deployStepped(
            first,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("a", "dev"), Deployment.Target.of("b", "dev"))),
            2_000);

    assertThat(Objects.requireNonNull(second.deployment()).targets())
        .extracting(target -> target.landing().merge())
        .containsExactly("cafe", null);
  }

  @Test
  void forgetsTheMergeOfRepositoryOnceItsRevertCommitIsRecorded() {
    TaskState task = TaskState.builder("a", "/wt", TaskStatus.DEPLOYED).build();
    TaskState deployed =
        reducer.deployStepped(
            reducer.deployStepped(
                task, new DeployStep.Opened(List.of(Deployment.Target.of("a", "dev"))), 1_000),
            new DeployStep.Merged("a", "cafe"),
            1_100);

    TaskState reverted =
        reducer.deployStepped(deployed, new DeployStep.Reverted("a", "beef"), 1_200);

    assertThat(Objects.requireNonNull(reverted.deployment()).live()).isFalse();
    assertThat(reverted.deployment().targets().getFirst().revertCommit()).isEqualTo("beef");
  }

  @Test
  void marksTheRepositoryWhoseDeployWorktreePathAnotherHeldAsBlocked() {
    TaskState task = TaskState.builder("a", "/wt", TaskStatus.REVIEWED).build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("a", "dev"), Deployment.Target.of("b", "dev"))),
            1_000);

    TaskState blocked = reducer.deployStepped(opened, new DeployStep.Blocked("b"), 1_100);

    assertThat(Objects.requireNonNull(blocked.deployment()).targets())
        .extracting(Deployment.Target::isBlocked)
        .containsExactly(false, true);
  }

  @Test
  void endsHostReadStopOnReadThatDisprovesIt() {
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.CI_POLLING)
            .stop(new TaskStop(TaskStop.Trigger.INFRA, 1_000, "runner died"))
            .build();

    TaskState ended = reducer.ended(stopped, TaskStop.Trigger.Ends.HOST_READ, "", 2_000);

    assertThat(ended.stop()).isNull();
  }

  @Test
  void escalatingLiftsTheStopAndRaisesTheTierOneRung() {
    TaskStop stop = new TaskStop(TaskStop.Trigger.STALLED, 1_000, "rounds bring nothing new");
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .stop(stop)
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 0));

    TaskState escalated = reducer.escalated(stopped, 3_000);

    assertThat(escalated.stop()).isNull();
    assertThat(Objects.requireNonNull(escalated.tier()).current()).isEqualTo("opus");
    assertThat(escalated.trailOrNone()).isEqualTo(RoundTrail.liftedAt(3_000));
  }

  @Test
  void plainLiftReturnsTheTierToTheModelTheSessionStartedOn() {
    TaskStop stop = new TaskStop(TaskStop.Trigger.STALLED, 1_000, "rounds bring nothing new");
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .stop(stop)
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 1));

    TaskState lifted = reducer.lift(stopped, 3_000);

    assertThat(Objects.requireNonNull(lifted.tier()).current()).isEqualTo("sonnet");
  }

  @Test
  void stopEndedByReadLeavesTheTierRaised() {
    TaskStop stop = new TaskStop(TaskStop.Trigger.CHECKS, 1_000, "unreadable");
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .stop(stop)
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 1));

    TaskState ended = reducer.ended(stopped, TaskStop.Trigger.Ends.HOST_READ, "", 2_000);

    assertThat(Objects.requireNonNull(ended.tier()).current()).isEqualTo("opus");
  }

  @Test
  void keepsPauseWhateverEndIsReportedUntilTheLift() {
    TaskStop pause = new TaskStop(TaskStop.Trigger.PAUSE, 1_000, "you paused it");
    TaskState paused = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).stop(pause).build();

    TaskState kept = reducer.ended(paused, TaskStop.Trigger.Ends.PAUSE, "", 2_000);
    TaskState lifted = reducer.lift(kept, 3_000);

    assertThat(kept.stop()).isEqualTo(pause);
    assertThat(lifted.stop()).isNull();
  }

  @Test
  void keepsLiftStopOnReadThatDisprovesHostReadStop() {
    TaskStop stop = new TaskStop(TaskStop.Trigger.STALLED, 1_000, "rounds bring nothing new");
    TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).stop(stop).build();

    TaskState kept = reducer.ended(stopped, TaskStop.Trigger.Ends.HOST_READ, "", 2_000);

    assertThat(kept.stop()).isEqualTo(stop);
  }

  @Test
  void keepsTheStopThatStandsWhenAnotherOneIsSet() {
    TaskStop standing = new TaskStop(TaskStop.Trigger.INFRA, 1_000, "runner died");
    TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.DEPLOYED).stop(standing).build();

    TaskState kept =
        reducer.stop(stopped, new TaskStop(TaskStop.Trigger.PACE, 2_000, "too fast"), 2_000);

    assertThat(kept.stop()).isEqualTo(standing);
  }

  @Test
  void requiresThePlanWhenTaskOpensInPlanModeBeforeTheCode() {
    TaskState planned =
        reducer.planAsked(TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING).build(), true);

    assertThat(planned.planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void opensTheGateWhenTheHumanApprovesRequiredPlan() {
    TaskState required =
        TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build();

    assertThat(reducer.planApproved(required).planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void keepsThePlanGateWhenTaskOpensInPlanModePastTheCode() {
    TaskState past =
        TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).planGate(PlanGate.CLOSED).build();

    assertThat(reducer.planAsked(past, true).planGate()).isEqualTo(PlanGate.CLOSED);
  }

  @Test
  void keepsRequiredPlanGateWhenTheSessionDeclaresNoPlan() {
    TaskState required =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).planGate(PlanGate.REQUIRED).build();

    assertThat(reducer.noPlanDeclared(required).planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void keepsTheDeployConflictWhereHeldReportLeavesTheTaskInIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
    TaskState opened =
        reducer.deployStepped(
            task, new DeployStep.Opened(List.of(Deployment.Target.of("api", "dev"))), 1_000);
    TaskState conflicted =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("api", Deployment.Cause.MERGE), 1_000);

    TaskState held =
        reducer.reduce(
            conflicted,
            new TaskMove.Reported(TaskStatus.IN_PROGRESS, false),
            "working",
            false,
            2_000);

    assertThat(held.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(Objects.requireNonNull(held.deployment()).conflictedTarget())
        .isEqualTo(Objects.requireNonNull(conflicted.deployment()).conflictedTarget());
  }

  @Test
  void liftsStopCountedOnRequestWhenAnotherRequestReplacesIt() {
    TaskState shipped =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withMrUrl("https://host/mr/571", 1_000)
            .withStop(
                new TaskStop(TaskStop.Trigger.SHIPS, 2, "4 ships", "https://host/mr/571", null))
            .withTrail(RoundTrail.none().withRound(new RoundTrail.Round(1, 4, "", "a", "")));

    TaskState reopened = reducer.relinked(shipped.withMrUrl("https://host/mr/573", 3_000), 3_000);

    assertThat(reopened.stopped()).isFalse();
    assertThat(reopened.trailOrNone()).isEqualTo(RoundTrail.liftedAt(3_000));
  }

  @Test
  void keepsStopWhenTheSameRequestIsWrittenAgain() {
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withMrUrl("https://host/mr/571", 1_000)
            .withStop(
                new TaskStop(TaskStop.Trigger.SHIPS, 2, "4 ships", "https://host/mr/571", null));

    TaskState again = reducer.relinked(stopped.withMrUrl("https://host/mr/571", 3_000), 3_000);

    assertThat(again.stop()).isEqualTo(stopped.stop());
  }

  @Test
  void keepsStopCountedOnNoRequestWhenRequestReplacesTheOneOpen() {
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withMrUrl("https://host/mr/571", 1_000)
            .withStop(new TaskStop(TaskStop.Trigger.ROUNDS, 2, "verify sent 3 rounds running"));

    TaskState reopened = reducer.relinked(stopped.withMrUrl("https://host/mr/573", 3_000), 3_000);

    assertThat(reopened.stop()).isEqualTo(stopped.stop());
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStop.Trigger.class,
      names = {"CHECKS", "UNCHANGED_FAILURE"})
  void liftsPipelineStopWhenRequestReplacesTheOneItWasCountedOn(TaskStop.Trigger trigger) {
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withMrUrl("https://host/mr/571", 1_000)
            .withStop(
                new TaskStop(trigger, 2, "evidence").countedOn(List.of("https://host/mr/571")));

    TaskState reopened = reducer.relinked(stopped.withMrUrl("https://host/mr/573", 3_000), 3_000);

    assertThat(reopened.stopped()).isFalse();
  }

  @Test
  void clearsPermissionTheSignStampedWhenWorkStartsAgain() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState asked =
        reducer.signed(
            task,
            new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK),
            1_000);

    TaskState started = reducer.signed(asked, new SessionSign.WorkStarted(), 2_000);

    assertThat(asked.report()).isEqualTo(AgentReport.PERMISSION);
    assertThat(started.report()).isEqualTo(AgentReport.PLAIN);
  }

  @Test
  void keepsPermissionTheSessionSaidItselfWhenWorkStartsAgain() {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.PERMISSION)
            .build();
    TaskState asked =
        reducer.signed(
            task,
            new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK),
            1_000);

    TaskState started = reducer.signed(asked, new SessionSign.WorkStarted(), 2_000);

    assertThat(started.report()).isEqualTo(AgentReport.PERMISSION);
  }

  @Test
  void keepsPermissionTheSessionReportedAfterPromptStampedItWhenWorkStartsAgain() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState asked =
        reducer.signed(
            task,
            new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK),
            1_000);
    TaskState reported = reducer.signed(asked, new SessionSign.Reported(), 2_000);

    TaskState started = reducer.signed(reported, new SessionSign.WorkStarted(), 3_000);

    assertThat(started.report()).isEqualTo(AgentReport.PERMISSION);
  }

  @Test
  void leavesQuestionTheSessionSaidWhenPromptIsReported() {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.QUESTION)
            .build();

    TaskState asked =
        reducer.signed(
            task,
            new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.SCREEN),
            1_000);

    assertThat(asked.report()).isEqualTo(AgentReport.QUESTION);
  }

  @Test
  void keepsTurnFailureWhenAnIdleNoticeFollowsIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState noticed = reducer.signed(failed, new SessionSign.TurnEnded(null), 2_000);

    assertThat(SessionPhase.of(noticed.session())).isEqualTo(SessionPhase.FAILED);
    assertThat(Objects.requireNonNull(noticed.session().halt()).cause()).isEqualTo("overloaded");
  }

  @Test
  void endsTurnFailureWhenTheSessionRestarts() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState restarted = reducer.signed(failed, new SessionSign.Restarted("compact"), 2_000);

    assertThat(SessionPhase.of(restarted.session())).isEqualTo(SessionPhase.IDLE);
  }

  @Test
  void worksAgainWhenFailedSessionStartsWork() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState started = reducer.signed(failed, new SessionSign.WorkStarted(), 2_000);

    assertThat(SessionPhase.of(started.session())).isEqualTo(SessionPhase.WORKING);
    assertThat(started.session().workStartedAt()).isEqualTo(2_000);
  }

  @Test
  void isGoneWithItsReasonWhenTheSessionEnds() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

    TaskState ended = reducer.signed(task, new SessionSign.Ended("prompt_input_exit"), 1_000);

    assertThat(SessionPhase.of(ended.session())).isEqualTo(SessionPhase.GONE);
    assertThat(Objects.requireNonNull(ended.session().halt()).cause())
        .isEqualTo("prompt_input_exit");
  }

  @Test
  void keepsTheLogFileSessionNamedWithoutMovingItsPhase() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState idle = reducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

    TaskState named = reducer.signed(idle, new SessionSign.LogNamed("/logs/s.jsonl"), 2_000);

    assertThat(named.session().log()).isEqualTo("/logs/s.jsonl");
    assertThat(named.session().idleSince()).isEqualTo(1_000);
    assertThat(SessionPhase.of(named.session())).isEqualTo(SessionPhase.IDLE);
  }

  @Test
  void isIdleWhenTurnEndsWhileWorking() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState working = reducer.signed(task, new SessionSign.WorkStarted(), 1_000);

    TaskState idle = reducer.signed(working, new SessionSign.TurnEnded("/logs/s.jsonl"), 2_000);

    assertThat(SessionPhase.of(idle.session())).isEqualTo(SessionPhase.IDLE);
    assertThat(idle.session().log()).isEqualTo("/logs/s.jsonl");
  }

  @ParameterizedTest
  @ValueSource(longs = {500, 1_000})
  void keepsHaltWhenCallIsNoNewerThanIt(long calledAt) {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState called = reducer.signed(failed, new SessionSign.Called(), calledAt);

    assertThat(SessionPhase.of(called.session())).isEqualTo(SessionPhase.FAILED);
  }

  @Test
  void dropsHaltWhenCallComesAfterIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState ended = reducer.signed(task, new SessionSign.Ended("prompt_input_exit"), 1_000);

    TaskState called = reducer.signed(ended, new SessionSign.Called(), 2_000);

    assertThat(SessionPhase.of(called.session())).isEqualTo(SessionPhase.WORKING);
  }

  @Test
  void sessionThatOnlyRunsToolsIsNotQuietPastTheLimit() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState working = reducer.signed(task, new SessionSign.WorkStarted(), 1_000);
    TaskState tooled = reducer.signed(working, new SessionSign.ToolUsed(), 8_000);

    TaskState read =
        reducer.signed(
            tooled, new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 5_000, null), 9_000);

    assertThat(SessionPhase.of(read.session())).isEqualTo(SessionPhase.WORKING);
    assertThat(read.session().quietSince()).isZero();
  }

  @ParameterizedTest
  @ValueSource(longs = {500, 1_000})
  void keepsHaltWhenToolUseIsNoNewerThanIt(long usedAt) {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState used = reducer.signed(failed, new SessionSign.ToolUsed(), usedAt);

    assertThat(SessionPhase.of(used.session())).isEqualTo(SessionPhase.FAILED);
  }

  @Test
  void dropsHaltWhenToolUseComesAfterIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState used = reducer.signed(failed, new SessionSign.ToolUsed(), 2_000);

    assertThat(SessionPhase.of(used.session())).isEqualTo(SessionPhase.WORKING);
  }

  @Test
  void toolUseIsNoReportOfTheTurn() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState working = reducer.signed(task, new SessionSign.WorkStarted(), 1_000);

    TaskState used = reducer.signed(working, new SessionSign.ToolUsed(), 2_000);

    assertThat(used.session().reportedAt()).isZero();
    assertThat(used.session().workStartedAt()).isEqualTo(1_000);
  }

  @Test
  void dropsHaltWhenWorkStartsInTheSameMillisecond() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState started = reducer.signed(failed, new SessionSign.WorkStarted(), 1_000);

    assertThat(SessionPhase.of(started.session())).isEqualTo(SessionPhase.WORKING);
  }

  @Test
  void goesQuietSinceTheTurnEndedWhenReadFindsNothingAfterItForLongerThanTheLimit() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState idle = reducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

    TaskState quiet =
        reducer.signed(
            idle, new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 5_000, null), 9_000);

    assertThat(SessionPhase.of(quiet.session())).isEqualTo(SessionPhase.QUIET);
    assertThat(quiet.session().quietSince()).isEqualTo(1_000);
  }

  @Test
  void sessionStartedOverIsNotEndedByTheEndSignOfTheCliItReplaced() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState fresh = reducer.sessionStartedOver(task);

    TaskState late = reducer.signed(fresh, new SessionSign.Ended("exited"), 9_000);

    assertThat(SessionPhase.of(late.session())).isNotEqualTo(SessionPhase.GONE);
  }

  @Test
  void sessionStartedOverIsNotQuietFromTheOldTranscriptBeforeItsFirstHook() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState fresh = reducer.sessionStartedOver(task);

    TaskState first =
        reducer.signed(
            fresh,
            new SessionSign.Read(Session.Window.State.RUNNING, 0, 1_000, 0, 5_000, null),
            9_000);
    TaskState second =
        reducer.signed(
            first,
            new SessionSign.Read(Session.Window.State.RUNNING, 0, 1_000, 0, 5_000, null),
            12_000);
    TaskState third =
        reducer.signed(
            second,
            new SessionSign.Read(Session.Window.State.RUNNING, 0, 1_000, 0, 5_000, null),
            14_000);

    assertThat(first.session().quietSince()).isZero();
    assertThat(second.session().quietSince()).isZero();
    assertThat(third.session().quietSince()).isEqualTo(9_000);
  }

  @Test
  void staysIdleWhenReadFindsTheTurnEndWithinTheLimit() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState idle = reducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

    TaskState read =
        reducer.signed(
            idle, new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 5_000, null), 4_000);

    assertThat(SessionPhase.of(read.session())).isEqualTo(SessionPhase.IDLE);
  }

  @Test
  void staysIdleAndGoesQuietSinceTheTurnEndedWhenTheWindowOnlyRepaintedAfterIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState idle = reducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

    TaskState quiet =
        reducer.signed(
            idle,
            new SessionSign.Read(Session.Window.State.RUNNING, 2_000, 0, 0, 5_000, null),
            9_000);

    assertThat(Objects.requireNonNull(quiet.session().halt()).kind())
        .isEqualTo(Session.Halt.Kind.IDLE);
    assertThat(quiet.session().quietSince()).isEqualTo(1_000);
  }

  @Test
  void endsAnEndedSessionWhenLaterReadFindsItsAgentProcessRunning() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState ended = reducer.signed(task, new SessionSign.Ended("boom"), 1_000);

    TaskState read =
        reducer.signed(
            ended,
            new SessionSign.Read(Session.Window.State.RUNNING, 8_000, 0, 0, 5_000, null),
            9_000);

    assertThat(SessionPhase.of(read.session())).isNotEqualTo(SessionPhase.GONE);
  }

  @Test
  void endsGoneSessionReadSetWhenLaterReadFindsItsAgentProcessRunning() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState gone =
        reducer.signed(
            task, new SessionSign.Read(Session.Window.State.MISSING, 0, 0, 0, 5_000, null), 2_000);

    TaskState read =
        reducer.signed(
            gone, new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 5_000, null), 3_000);

    assertThat(SessionPhase.of(read.session())).isNotEqualTo(SessionPhase.GONE);
  }

  @Test
  void keepsFailedSessionFailedWhenLaterReadFindsItsAgentProcessRunning() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState read =
        reducer.signed(
            failed,
            new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 5_000, null),
            3_000);

    assertThat(SessionPhase.of(read.session())).isEqualTo(SessionPhase.FAILED);
  }

  @Test
  void endsFailureWhenTheSessionsLogGrewAfterIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState read =
        reducer.signed(
            failed,
            new SessionSign.Read(Session.Window.State.UNREAD, 0, 2_000, 0, 5_000, null),
            3_000);

    assertThat(SessionPhase.of(read.session())).isEqualTo(SessionPhase.WORKING);
  }

  @Test
  void endsTheSessionWithWhatItPrintedWhenReadFindsItsWindowHoldingNoAgent() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

    TaskState read =
        reducer.signed(
            task,
            new SessionSign.Read(
                Session.Window.State.DEAD_SHELL, 0, 0, 0, 5_000, "Error: bad config"),
            9_000);

    assertThat(SessionPhase.of(read.session())).isEqualTo(SessionPhase.GONE);
    assertThat(Objects.requireNonNull(read.session().halt()).cause())
        .isEqualTo("Error: bad config");
  }

  @Test
  void keepsTheCauseAnEndedSessionGaveWhenReadFindsItsWindowGone() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState ended = reducer.signed(task, new SessionSign.Ended("boom"), 8_000);

    TaskState read =
        reducer.signed(
            ended, new SessionSign.Read(Session.Window.State.MISSING, 0, 0, 0, 5_000, null), 9_000);

    assertThat(read.session().halt()).isEqualTo(ended.session().halt());
  }

  @Test
  void secondGitChangeFoundReplacesTheOneParked() {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .gitTrust(GitTrust.of(Map.of("a", "1")))
            .build();
    TaskState first = reducer.gitChanged(task, "a changed");

    TaskState second = reducer.gitChanged(first, "a changed; b added");

    assertThat(Objects.requireNonNull(second.gitTrust()).changed()).isEqualTo("a changed; b added");
  }

  @Test
  void repositoryAttachedEndsTheRequestForOne() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState asked = reducer.repoAsked(task, "registry");

    TaskState attached = reducer.repoAttached(asked, TaskRepo.of("registry", "/wt-registry"));

    assertThat(attached.asksForRepository()).isFalse();
    assertThat(attached.repos()).extracting(TaskRepo::project).contains("registry");
  }

  @Test
  void placesTaskInTheRepositoriesItsPlanNamedInPlaceOfItsScratchFolder() {
    TaskState task =
        TaskState.builder(List.of(TaskRepo.of(null, "/scratch/ABC-9")), TaskStatus.PLAN_PENDING)
            .baseBranch("origin/feature/parent")
            .build();

    TaskState placed =
        reducer.placed(
            task,
            List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("web", "/wt-web")),
            "feature/parent");

    assertThat(placed.projects()).containsExactly("api", "web");
    assertThat(placed.status()).isEqualTo(TaskStatus.PLAN_PENDING);
    assertThat(placed.baseBranch()).isEqualTo("feature/parent");
  }

  @Test
  void placesTaskThatAskedForRepositoryWithoutKeepingTheRequest() {
    TaskState task =
        reducer.repoAsked(
            TaskState.builder(List.of(TaskRepo.of(null, "/scratch/ABC-9")), TaskStatus.PLAN_PENDING)
                .build(),
            "registry");

    TaskState placed = reducer.placed(task, List.of(TaskRepo.of("api", "/wt-api")), null);

    assertThat(placed.asksForRepository()).isFalse();
    assertThat(placed.baseBranch()).isNull();
  }

  @Test
  void reportNeedingNoRepositoryEndsTheRequestForOne() {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).repoRequest("registry").build();

    TaskState reported = reducer.repoAsked(task, null);

    assertThat(reported.asksForRepository()).isFalse();
  }

  @Test
  void newHeadMakesTheChecksReadOnTheOldOneStale() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).build();
    TaskState read = reducer.checksRead(reducer.headSeen(task, "t1"), "failed", "t1");

    TaskState moved = reducer.headSeen(read, "t2");

    assertThat(read.checksRead()).isEqualTo("failed");
    assertThat(moved.checksRead()).isNull();
  }

  @Test
  void anApprovalReadIsKeptAsTheHostSaidIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.REVIEWED).build();

    TaskState approved = reducer.approvalRead(task, true);

    assertThat(approved.approved()).isTrue();
  }

  @Test
  void disarmsTheInfraRetryArmedForTheCommitBeforeWhenTheHeadMoves() {
    InfraRetry armed =
        InfraRetry.armed(
            9_000, "shop", "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3);
    TaskState read =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .headTip("aaa")
            .infraRetries(List.of(armed))
            .build();

    TaskState moved = reducer.headSeen(read, "bbb");

    assertThat(read.infraRetries()).containsExactly(armed);
    assertThat(moved.infraRetries()).isEmpty();
  }

  @Test
  void keepsTheInfraRetryArmedWhenTheSameHeadIsSeenAgain() {
    InfraRetry armed =
        InfraRetry.armed(
            9_000, "shop", "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3);
    TaskState read =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .headTip("aaa")
            .infraRetries(List.of(armed))
            .build();

    TaskState again = reducer.headSeen(read, "aaa");

    assertThat(again.infraRetries()).containsExactly(armed);
  }

  @Test
  void forgetsWhatWasReadAboutRequestWhenTheTaskIsPointedAtAnotherOne() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .mrUrl("https://host/mr/1")
            .approved(true)
            .pipelineStatus("success")
            .requestOpenedAt(1_700_000_000_000L)
            .build();

    TaskState relinked =
        reducer.requestLinked(task, Map.of(), "https://host/mr/2", false, 1_700_000_005_000L);

    assertThat(relinked.approved()).isNull();
    assertThat(relinked.pipelineStatus()).isNull();
    assertThat(relinked.requestOpenedAt()).isEqualTo(1_700_000_005_000L);
  }

  @Test
  void forgetsTheInfraRetriesOfRoundWhenNewRoundIsShipped() {
    TaskState polling =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("https://host/mr/9")
            .infraRetries(
                List.of(
                    InfraRetry.armed(
                        5_000,
                        "shop",
                        "https://host/p/1",
                        "unit-tests",
                        "runner_system_failure",
                        1,
                        3)))
            .build();

    TaskState round = reducer.requestLinked(polling, Map.of(), "https://host/mr/9", true, 2_000L);

    assertThat(round.infraRetries()).isEmpty();
  }

  @Test
  void readsTheRequestsMergedOnceTheOnlyOpenOneLeavesTheTask() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                    new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                TaskStatus.CI_POLLING)
            .build()
            .withMergedByHost("api");

    TaskState left = reducer.repoLeft(task, "web");

    assertThat(left.projects()).containsExactly("api");
    assertThat(left.requestsMerged()).isTrue();
  }

  @Test
  void keepsTheOnlyRepositoryOfTaskWhoseRequestWasClosed() {
    TaskState task =
        TaskState.builder(
                List.of(new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api")),
                TaskStatus.CI_POLLING)
            .build();

    TaskState left = reducer.repoLeft(task, "api");

    assertThat(left.projects()).containsExactly("api");
  }

  @Test
  void keepsTheLandedTargetInTheRecordWhereItsRepositoryLeavesTheTask() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta"),
                    new TaskRepo("gamma", "/gamma-wt", "git@host:g/gamma.git", "http://mr/gamma")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(
                    Deployment.Target.of("alpha", "dev"),
                    Deployment.Target.of("beta", "dev"),
                    Deployment.Target.of("gamma", "dev"))),
            1_000);
    TaskState merged =
        reducer.deployStepped(
            reducer.deployStepped(
                reducer.deployStepped(opened, new DeployStep.Merged("alpha", "a1"), 1_100),
                new DeployStep.Merged("beta", "b1"),
                1_100),
            new DeployStep.Merged("gamma", "c1"),
            1_100);
    TaskState red =
        reducer.deployStepped(
            merged, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_200);

    TaskState left = reducer.repoLeft(red, "beta");

    assertThat(left.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(left.projects()).containsExactly("alpha", "gamma");
    assertThat(Objects.requireNonNull(left.deployment()).targets())
        .extracting(Deployment.Target::project)
        .containsExactly("alpha", "beta", "gamma");
    assertThat(left.deployment().tips()).contains("beta@b1");
    assertThat(left.deployment().targets()).noneMatch(Deployment.Target::conflicted);
  }

  @Test
  void leavesTheDeployLandingSoItsCheckIsAskedAgainWhereTheConflictedRepositoryLeavesTheTask() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
            1_000);
    TaskState merged =
        reducer.deployStepped(
            reducer.deployStepped(opened, new DeployStep.Merged("alpha", "a1"), 1_100),
            new DeployStep.Merged("beta", "b1"),
            1_100);
    TaskState red =
        reducer.deployStepped(
            merged, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_200);

    TaskState left = reducer.repoLeft(red, "beta");
    TaskState asked = reducer.deployStepped(left, new DeployStep.Asked("dg"), 1_300);

    assertThat(DeployPhase.of(left.deployment())).isEqualTo(DeployPhase.LANDING);
    assertThat(DeployPhase.of(asked.deployment())).isEqualTo(DeployPhase.CHECK_ASKED);
  }

  @Test
  void keepsTheDeployConflictWhereTheConflictedRepositoryIsTheTasksOnlyOne() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task, new DeployStep.Opened(List.of(Deployment.Target.of("alpha", "dev"))), 1_000);
    TaskState red =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("alpha", Deployment.Cause.RED_PIPELINE), 1_100);

    TaskState left = reducer.repoLeft(red, "alpha");

    assertThat(left.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(Objects.requireNonNull(left.deployment()).conflictedTarget()).isPresent();
  }

  @Test
  void offersDeployAgainWhereTheConflictedRepositoryLeavesAndNothingLanded() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
            1_000);
    TaskState conflicted =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("beta", Deployment.Cause.MERGE), 1_100);

    TaskState left = reducer.repoLeft(conflicted, "beta");

    assertThat(left.status()).isEqualTo(TaskStatus.REVIEWED);
    assertThat(Objects.requireNonNull(left.deployment()).conflictedTarget()).isEmpty();
  }

  @Test
  void holdsTheDeployConflictOnTheUnpushedRepositoryWhereTheMergeConflictedOneLeavesTheTask() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta"),
                    new TaskRepo("gamma", "/gamma-wt", "git@host:g/gamma.git", "http://mr/gamma")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(
                    Deployment.Target.of("alpha", "dev"),
                    Deployment.Target.of("beta", "dev"),
                    Deployment.Target.of("gamma", "dev"))),
            1_000);
    TaskState merged = reducer.deployStepped(opened, new DeployStep.Merged("alpha", "a1"), 1_100);
    TaskState conflicted =
        reducer.deployStepped(
            merged, new DeployStep.Conflicted("beta", Deployment.Cause.MERGE), 1_200);

    TaskState left = reducer.repoLeft(conflicted, "beta");

    assertThat(left.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(left.projects()).containsExactly("alpha", "gamma");
    assertThat(Objects.requireNonNull(left.deployment()).targets())
        .extracting(Deployment.Target::project)
        .containsExactly("alpha", "gamma");
    assertThat(left.deployment().at()).isEqualTo(1);
    assertThat(left.deployment().conflictedTarget()).isEmpty();
    assertThat(left.deployment().targets().getFirst().landing().merge()).isEqualTo("a1");
    assertThat(DeployPhase.of(left.deployment())).isEqualTo(DeployPhase.LANDING);
  }

  @Test
  void landsDeployedWhereTheMergeConflictedLastRepositoryLeavesAfterAnotherLanded() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
            1_000);
    TaskState merged = reducer.deployStepped(opened, new DeployStep.Merged("alpha", "a1"), 1_100);
    TaskState conflicted =
        reducer.deployStepped(
            merged, new DeployStep.Conflicted("beta", Deployment.Cause.MERGE), 1_200);

    TaskState left = reducer.repoLeft(conflicted, "beta");

    assertThat(left.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(Objects.requireNonNull(left.deployment()).targets())
        .extracting(Deployment.Target::project)
        .containsExactly("alpha");
    assertThat(left.deployment().conflictedTarget()).isEmpty();
  }

  @Test
  void leavesNothingToPushWhereThePipelineConflictedRepositoryLeavesTheTask() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta"),
                    new TaskRepo("gamma", "/gamma-wt", "git@host:g/gamma.git", "http://mr/gamma")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(
                    Deployment.Target.of("alpha", "dev"),
                    Deployment.Target.of("beta", "dev"),
                    Deployment.Target.of("gamma", "dev"))),
            1_000);
    TaskState red =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_200);

    TaskState left = reducer.repoLeft(red, "beta");

    assertThat(Objects.requireNonNull(left.deployment()).at()).isEqualTo(2);
  }

  @Test
  void keepsTheDeployConflictWhereAnotherRepositoryThanTheConflictedOneLeavesTheTask() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta"),
                    new TaskRepo("gamma", "/gamma-wt", "git@host:g/gamma.git", "http://mr/gamma")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(
                    Deployment.Target.of("alpha", "dev"),
                    Deployment.Target.of("beta", "dev"),
                    Deployment.Target.of("gamma", "dev"))),
            1_000);
    TaskState red =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_200);

    TaskState left = reducer.repoLeft(red, "gamma");

    assertThat(left.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(Objects.requireNonNull(left.deployment()).conflictedTarget().orElseThrow().project())
        .isEqualTo("beta");
  }
}
