package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.stop.TaskStop;
import java.util.Optional;

/**
 * One step's row: the retries, then the restarts a missed beat earns before its named stop, and the
 * reads a red result is given before it lands. A step with no stop waits on a missed beat.
 */
record StepPolicy(ProgressStep step, int retries, int restarts, int redReads) {

  static StepPolicy of(ProgressStep step) {
    return switch (step) {
      case ProgressStep.SessionTurn turn -> new StepPolicy(turn, 1, 1, 1);
      case ProgressStep.Verifying verification -> new StepPolicy(verification, 1, 0, 1);
      case ProgressStep.HostRead read -> new StepPolicy(read, 0, 0, 1);
      case ProgressStep.InfraRerun rerun -> new StepPolicy(rerun, 2, 0, 2);
      case ProgressStep.JudgeRound round -> new StepPolicy(round, 0, 0, 0);
      case ProgressStep.Deploy deploy -> new StepPolicy(deploy, 0, 0, 1);
    };
  }

  Optional<TaskMove> stop(long now) {
    return switch (step) {
      case ProgressStep.SessionTurn turn -> stopped(TaskStop.Trigger.UNDELIVERED, now);
      case ProgressStep.Verifying verification -> stopped(TaskStop.Trigger.UNCHANGED_FAILURE, now);
      case ProgressStep.HostRead read -> stopped(TaskStop.Trigger.CHECKS, now);
      case ProgressStep.InfraRerun rerun -> stopped(TaskStop.Trigger.INFRA, now);
      case ProgressStep.JudgeRound round -> Optional.empty();
      case ProgressStep.Deploy deploy -> stopped(TaskStop.Trigger.DEPLOY_UNREAD, now);
    };
  }

  private Optional<TaskMove> stopped(TaskStop.Trigger trigger, long now) {
    return Optional.of(new TaskMove.StopSet(new TaskStop(trigger, now, trigger.reason())));
  }
}
