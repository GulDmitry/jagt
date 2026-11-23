package dev.jagt.orchestrator.flow.role;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.master.Master;

/**
 * The master region's states: the Master stands in for the human, read the round in front, or left
 * it to the human.
 */
public enum MasterPhase {
  STANDS_IN,
  READ,
  LEFT;

  public static MasterPhase of(TaskState task) {
    Master master = task.work().arbiterOrNone();
    if (!master.left().isEmpty()
        || (task.standing().report().asks()
            && master.unanswered().equals(task.standing().askedDigest()))
        || (FlowRules.deployedByTheArbiter(task.standing().status())
            && master.pressedDeploy().equals(String.valueOf(task.standing().statusSince())))) {
      return LEFT;
    }
    return readTheRoundInFront(task) ? READ : STANDS_IN;
  }

  /**
   * A verdict jagt wrote and its file still stands for was read for the turn in front of a status
   * the Master reads.
   */
  public static boolean readTheRoundInFront(TaskState task) {
    Master master = task.work().arbiterOrNone();
    return !master.seal().isEmpty()
        && master.sealedRound() == task.work().turnOrNone().id()
        && FlowRules.readByTheArbiter(task.standing().status());
  }
}
