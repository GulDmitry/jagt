package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.TaskState;

/** The master region's states: the Master stands in for the human, read the round in front, or left it to the human. */
public enum MasterPhase {
    STANDS_IN, READ, LEFT;

    public static MasterPhase of(TaskState task) {
        Master master = task.masterOrNone();
        if (!master.left().isEmpty() || task.report().asks() && master.unanswered().equals(task.askedDigest())
                || FlowRules.deployedByTheMaster(task.status())
                && master.pressedDeploy().equals(String.valueOf(task.statusSince()))) {
            return LEFT;
        }
        boolean sealed = !master.seal().isEmpty() && master.sealedRound() == task.turnOrNone().id();
        return sealed && FlowRules.readByTheMaster(task.status()) ? READ : STANDS_IN;
    }
}
