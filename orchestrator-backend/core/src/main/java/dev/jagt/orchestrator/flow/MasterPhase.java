package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.TaskState;

/**
 * The master region's states: the Master stands in for the human, read the round in front, or left
 * it to the human.
 */
public enum MasterPhase {
  STANDS_IN,
  READ,
  LEFT;

  public static MasterPhase of(TaskState task) {
    Master master = task.masterOrNone();
    if (!master.left().isEmpty()
        || (task.report().asks() && master.unanswered().equals(task.askedDigest()))
        || (FlowRules.deployedByTheMaster(task.status())
            && master.pressedDeploy().equals(String.valueOf(task.statusSince())))) {
      return LEFT;
    }
    return readTheRoundInFront(task) ? READ : STANDS_IN;
  }

  /**
   * A verdict jagt wrote and its file still stands for was read for the turn in front of a status
   * the Master reads.
   */
  public static boolean readTheRoundInFront(TaskState task) {
    Master master = task.masterOrNone();
    return !master.seal().isEmpty()
        && master.sealedRound() == task.turnOrNone().id()
        && FlowRules.readByTheMaster(task.status());
  }
}
