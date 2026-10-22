package dev.jagt.orchestrator.task;

/** Whether the plan, and the visual where asked for, must be approved before any other file changes. */
public enum PlanGate {

    /** Nothing to wait for. */
    OPEN,
    /** Opened by autoplan: approval opens it, and the session may declare the task needs no plan. */
    CLOSED,
    /** Opened in plan mode: only an approval opens it. */
    REQUIRED;

    public boolean closed() {
        return this != OPEN;
    }
}
