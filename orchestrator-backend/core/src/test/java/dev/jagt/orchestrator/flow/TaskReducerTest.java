package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.stop.StopTriage;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import dev.jagt.orchestrator.task.usage.ModelTier;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TaskReducerTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {});
  private final TaskState conflicted =
      reducer.deployStepped(
          reducer.deployStepped(
              TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT).build(),
              new DeployStep.Opened(List.of(Deployment.Target.of("api", "dev"))),
              1_000),
          new DeployStep.Conflicted("api", Deployment.Cause.MERGE),
          1_000);

  @Test
  void keepsTheSilenceToldWhereTheAutoReviewPingsAreForgotten() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState told =
        reducer.told(
            reducer.told(task, ToldOnce.Kind.AUTO_REVIEW_WINDOW, "5"), ToldOnce.Kind.SILENCE, "9");

    TaskState forgot =
        reducer.untold(
            told, ToldOnce.Kind.AUTO_REVIEW_WINDOW, ToldOnce.Kind.AUTO_REVIEW_UNREADABLE);

    assertThat(forgot.round().toldOrNone().entries()).containsOnlyKeys(ToldOnce.Kind.SILENCE);
  }

  @Test
  void tellsPointOncePerKeyAndLeavesTheKindsAlone() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

    TaskState told =
        reducer.told(reducer.told(task, ToldOnce.Kind.SILENCE, "9"), Point.DEPLOYED, "abc");

    assertThat(told.round().toldOrNone().saidPoint("deployed", "abc")).isTrue();
    assertThat(told.round().toldOrNone().saidPoint("deployed", "def")).isFalse();
    assertThat(told.round().toldOrNone().said(ToldOnce.Kind.SILENCE, "9")).isTrue();
  }

  @Test
  void forgetsTheDeployConflictOnceTheDeployLands() {
    TaskState deployed =
        reducer.reduce(
            conflicted,
            new TaskMove.Ran(TaskAction.DEPLOY, Outcome.Kind.OK),
            "deployed",
            true,
            2_000);

    assertThat(
            Objects.requireNonNull(deployed.work().deployment())
                .targets()
                .getFirst()
                .progress()
                .conflict())
        .isNull();
  }

  @Test
  void turnsStalledConflictIntoTheSessionsQuestionAndRestartsItsClock() {
    TaskState referred = reducer.conflictReferred(conflicted, "stalled for 20 min", 2_000);

    assertThat(referred.standing().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(referred.standing().report()).isEqualTo(AgentReport.QUESTION);
    assertThat(referred.standing().message()).isEqualTo("stalled for 20 min");
    assertThat(referred.currentConflict().orElseThrow().askedTheArbiter()).isTrue();
    assertThat(referred.currentConflict().orElseThrow().movedAt()).isEqualTo(2_000);
    assertThat(Move.asking(referred.standing().status(), referred.standing().report())).isTrue();
  }

  @Test
  void asksTheMasterOfStalledConflictOnlyOnce() {
    TaskState referred = reducer.conflictReferred(conflicted, "first", 2_000);

    TaskState again = reducer.conflictReferred(referred, "second", 3_000);

    assertThat(again.standing().message()).isEqualTo("first");
    assertThat(again.currentConflict().orElseThrow().movedAt()).isEqualTo(2_000);
  }

  @Test
  void asksNothingWhereNoConflictStands() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.DEPLOYED).build();

    TaskState referred = reducer.conflictReferred(task, "stalled", 2_000);

    assertThat(referred.standing().report()).isEqualTo(AgentReport.PLAIN);
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

    assertThat(
            Objects.requireNonNull(retried.work().deployment()).targets().getFirst().retriedTip())
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

    assertThat(Objects.requireNonNull(conflicted.work().deployment()).at()).isEqualTo(1);
    assertThat(conflicted.work().deployment().targets().get(0).landing().merge()).isEqualTo("cafe");
    assertThat(conflicted.work().deployment().targets().get(1).conflicted()).isTrue();
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

    assertThat(Objects.requireNonNull(second.work().deployment()).targets())
        .extracting(target -> target.landing().merge())
        .containsExactly("cafe", null);
  }

  @Test
  void keepsThePushAndPipelineReadOfLiveDeployWhenTheNextPressOpens() {
    TaskState task = TaskState.builder("a", "/wt", TaskStatus.REVIEWED).build();
    TaskState landed =
        reducer.deployStepped(
            reducer.deployStepped(
                reducer.deployStepped(
                    task, new DeployStep.Opened(List.of(Deployment.Target.of("a", "dev"))), 1_000),
                new DeployStep.Merged("a", "cafe"),
                1_100),
            new DeployStep.Pushed("a", 1_200),
            1_200);

    TaskState pressedAgain =
        reducer.deployStepped(
            landed, new DeployStep.Opened(List.of(Deployment.Target.of("a", "dev"))), 2_000);

    assertThat(
            Objects.requireNonNull(pressedAgain.work().deployment())
                .targets()
                .getFirst()
                .landing()
                .pushedAt())
        .isEqualTo(1_200);
  }

  @Test
  void decidesDeployOnlyOnceThePipelineOnItsTipReadGreen() {
    TaskState task = TaskState.builder("a", "/wt", TaskStatus.DEPLOYED).build();
    TaskState landed =
        reducer.deployStepped(
            reducer.deployStepped(
                task, new DeployStep.Opened(List.of(Deployment.Target.of("a", "dev"))), 1_000),
            new DeployStep.Merged("a", "cafe"),
            1_100);

    TaskState green = reducer.deployStepped(landed, new DeployStep.Green(), 1_200);
    TaskState fixed = reducer.deployStepped(green, new DeployStep.Fixed("a", "beef"), 1_300);

    assertThat(Objects.requireNonNull(landed.work().deployment()).undecided()).isTrue();
    assertThat(Objects.requireNonNull(green.work().deployment()).undecided()).isFalse();
    assertThat(Objects.requireNonNull(fixed.work().deployment()).undecided()).isTrue();
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

    assertThat(Objects.requireNonNull(reverted.work().deployment()).live()).isFalse();
    assertThat(reverted.work().deployment().targets().getFirst().revertCommit()).isEqualTo("beef");
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

    assertThat(Objects.requireNonNull(blocked.work().deployment()).targets())
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

    assertThat(ended.work().stop()).isNull();
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

    assertThat(escalated.work().stop()).isNull();
    assertThat(Objects.requireNonNull(escalated.work().tier()).current()).isEqualTo("opus");
    assertThat(escalated.round().trailOrNone()).isEqualTo(RoundTrail.liftedAt(3_000));
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

    assertThat(Objects.requireNonNull(lifted.work().tier()).current()).isEqualTo("sonnet");
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

    assertThat(Objects.requireNonNull(ended.work().tier()).current()).isEqualTo("opus");
  }

  @Test
  void keepsPauseWhateverEndIsReportedUntilTheLift() {
    TaskStop pause = new TaskStop(TaskStop.Trigger.PAUSE, 1_000, "you paused it");
    TaskState paused = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).stop(pause).build();

    TaskState kept = reducer.ended(paused, TaskStop.Trigger.Ends.PAUSE, "", 2_000);
    TaskState lifted = reducer.lift(kept, 3_000);

    assertThat(kept.work().stop()).isEqualTo(pause);
    assertThat(lifted.work().stop()).isNull();
  }

  @Test
  void keepsLiftStopOnReadThatDisprovesHostReadStop() {
    TaskStop stop = new TaskStop(TaskStop.Trigger.STALLED, 1_000, "rounds bring nothing new");
    TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).stop(stop).build();

    TaskState kept = reducer.ended(stopped, TaskStop.Trigger.Ends.HOST_READ, "", 2_000);

    assertThat(kept.work().stop()).isEqualTo(stop);
  }

  @Test
  void keepsTheStopThatStandsWhenAnotherOneIsSet() {
    TaskStop standing = new TaskStop(TaskStop.Trigger.INFRA, 1_000, "runner died");
    TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.DEPLOYED).stop(standing).build();

    TaskState kept =
        reducer.stop(stopped, new TaskStop(TaskStop.Trigger.PACE, 2_000, "too fast"), 2_000);

    assertThat(kept.work().stop()).isEqualTo(standing);
  }

  @Test
  void requiresThePlanWhenTaskOpensInPlanModeBeforeTheCode() {
    TaskState planned =
        reducer.planAsked(TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING).build(), true);

    assertThat(planned.gate().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void opensTheGateWhenTheHumanApprovesRequiredPlan() {
    TaskState required =
        TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build();

    assertThat(reducer.planApproved(required).gate().planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void marksThePlanApprovedSoTheNextOneIsLater() {
    TaskState planned = TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING).build();

    assertThat(planned.gate().planApproved()).isFalse();
    assertThat(reducer.planApproved(planned).gate().planApproved()).isTrue();
  }

  @Test
  void keepsThePlanGateWhenTaskOpensInPlanModePastTheCode() {
    TaskState past =
        TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).planGate(PlanGate.CLOSED).build();

    assertThat(reducer.planAsked(past, true).gate().planGate()).isEqualTo(PlanGate.CLOSED);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEW_PENDING", "CI_POLLING", "REVIEWED"})
  void opensTheGateWhenReviewRoundIsRelayedToTaskPastItsPlan(TaskStatus past) {
    TaskState closed = TaskState.builder("api", "/wt", past).planGate(PlanGate.CLOSED).build();

    assertThat(reducer.relayed(closed, "round", "d1", "", 1).gate().planGate())
        .isEqualTo(PlanGate.OPEN);
  }

  @Test
  void keepsTheGateClosedWhenTheTurnOfTaskPastItsPlanCloses() {
    TaskState closed =
        TaskState.builder("api", "/wt", TaskStatus.REVIEW_PENDING)
            .planGate(PlanGate.CLOSED)
            .build();

    assertThat(reducer.turnClosed(closed, 1).gate().planGate()).isEqualTo(PlanGate.CLOSED);
  }

  @Test
  void keepsTheGateClosedWhenRelayReachesTaskStillInItsPlan() {
    TaskState planning =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).planGate(PlanGate.CLOSED).build();

    assertThat(reducer.relayed(planning, "answer", "d1", "", 1).gate().planGate())
        .isEqualTo(PlanGate.CLOSED);
  }

  @Test
  void keepsRequiredPlanGateWhenTheSessionDeclaresNoPlan() {
    TaskState required =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).planGate(PlanGate.REQUIRED).build();

    assertThat(reducer.noPlanDeclared(required).gate().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void keepsTheDeployConflictWhereHeldReportLeavesTheTaskInIt() {
    TaskState held =
        reducer.reduce(
            conflicted,
            new TaskMove.Reported(TaskStatus.IN_PROGRESS, false),
            "working",
            false,
            2_000);

    assertThat(held.standing().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(Objects.requireNonNull(held.work().deployment()).conflictedTarget())
        .isEqualTo(Objects.requireNonNull(conflicted.work().deployment()).conflictedTarget());
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

    assertThat(reopened.work().stopped()).isFalse();
    assertThat(reopened.round().trailOrNone()).isEqualTo(RoundTrail.liftedAt(3_000));
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

    assertThat(again.work().stop()).isEqualTo(stopped.work().stop());
  }

  @Test
  void keepsStopCountedOnNoRequestWhenRequestReplacesTheOneOpen() {
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withMrUrl("https://host/mr/571", 1_000)
            .withStop(new TaskStop(TaskStop.Trigger.ROUNDS, 2, "verify sent 3 rounds running"));

    TaskState reopened = reducer.relinked(stopped.withMrUrl("https://host/mr/573", 3_000), 3_000);

    assertThat(reopened.work().stop()).isEqualTo(stopped.work().stop());
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

    assertThat(reopened.work().stopped()).isFalse();
  }

  @Test
  void keepsTheFirstReadWhenLaterReadOfTheSameStopArrives() {
    StopTriage first =
        new StopTriage(
            StopTriage.Who.JAGT,
            "ships counted past a merge",
            "H",
            StopTriage.Lift.SAFE,
            "merged",
            "");
    TaskState triaged =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withStop(new TaskStop(TaskStop.Trigger.SHIPS, 2, "4 ships").withTriage(first));

    TaskState later = reducer.triaged(triaged, "SHIPS@2", StopTriage.failed("the read timed out"));

    assertThat(later.work().stop()).isEqualTo(triaged.work().stop());
  }
}
