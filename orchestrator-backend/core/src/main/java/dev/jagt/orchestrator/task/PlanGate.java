package dev.jagt.orchestrator.task;

/** Whether the plan, and the visual where asked for, must be approved before any other file changes. */
public enum PlanGate {

    /** Nothing to wait for. */
    OPEN,
    /** Opened by autoplan: approval opens it, and the session may declare the task needs no plan. */
    CLOSED,
    /** Asked for in plan mode: only the human's approval opens it. */
    REQUIRED;

    public boolean keptForTheHuman() {
        return this == REQUIRED;
    }

    public boolean closed() {
        return this != OPEN;
    }
}
