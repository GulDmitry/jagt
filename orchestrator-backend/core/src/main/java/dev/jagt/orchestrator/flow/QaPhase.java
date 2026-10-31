package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

/** The qa region's states: nothing of QA's on the deploy, a read running, a round out the session has not settled, or a pass. */
public enum QaPhase {
    NONE, READING, ROUND_OPEN, PASSED;

    public static QaPhase of(TaskState task) {
        return task.qaOrNone().reading().isEmpty() ? beneath(task) : READING;
    }

    /** What the deploy reads as were no QA read running on it. */
    static QaPhase beneath(TaskState task) {
        if (task.qaRoundOpen()) {
            return ROUND_OPEN;
        }
        return task.qaPassedOnTips() ? PASSED : NONE;
    }
}
