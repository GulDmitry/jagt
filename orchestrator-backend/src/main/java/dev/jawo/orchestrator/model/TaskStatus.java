package dev.jawo.orchestrator.model;

public enum TaskStatus {
    NEW,
    IN_PROGRESS,
    REVIEW_PENDING,
    CI_POLLING,
    CI_FAILED,
    DONE
}
