package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

/** A checker region's states: nothing of the checker's on the deploy, a read running, a round out the session has not settled, or a pass. */
public enum CheckerPhase {
    NONE, READING, ROUND_OPEN, PASSED;

    public static CheckerPhase of(TaskState task) {
        return task.qaOrNone().reading().isEmpty() ? beneath(task) : READING;
    }

    /** What the deploy reads as were no QA read running on it. */
    static CheckerPhase beneath(TaskState task) {
        if (task.qaRoundOpen()) {
            return ROUND_OPEN;
        }
        return task.qaPassedOnTips() ? PASSED : NONE;
    }
}
