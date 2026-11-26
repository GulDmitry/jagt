package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.task.stop.TaskStop;

/** One step's row: the retries, then the restarts a missed beat earns before its named stop. */
record StepPolicy(ProgressStep step, int retries, int restarts) {

  static StepPolicy of(ProgressStep step) {
    return switch (step) {
      case ProgressStep.SessionTurn turn -> new StepPolicy(turn, 1, 1);
      case ProgressStep.Verifying verification -> new StepPolicy(verification, 1, 0);
      case ProgressStep.HostRead read -> new StepPolicy(read, 1, 0);
      case ProgressStep.InfraRerun rerun -> new StepPolicy(rerun, 2, 0);
      case ProgressStep.JudgeRound round -> new StepPolicy(round, 1, 0);
      case ProgressStep.Deploy deploy -> new StepPolicy(deploy, 1, 0);
    };
  }

  TaskMove stop(long now) {
    return switch (step) {
      case ProgressStep.SessionTurn turn -> stopped(TaskStop.Trigger.UNDELIVERED, now);
      case ProgressStep.Verifying verification -> stopped(TaskStop.Trigger.UNCHANGED_FAILURE, now);
      case ProgressStep.HostRead read -> stopped(TaskStop.Trigger.CHECKS, now);
      case ProgressStep.InfraRerun rerun -> stopped(TaskStop.Trigger.INFRA, now);
      case ProgressStep.JudgeRound round ->
          new TaskMove.MasterNoted(new MasterNote.Left("judge round"), MasterPhase.LEFT);
      case ProgressStep.Deploy deploy -> stopped(TaskStop.Trigger.DEPLOY_UNREAD, now);
    };
  }

  private TaskMove stopped(TaskStop.Trigger trigger, long now) {
    return new TaskMove.StopSet(new TaskStop(trigger, now, trigger.reason()));
  }
}
