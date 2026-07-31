package dev.jagt.orchestrator.model;

public enum TaskStatus {
    NEW,
    IN_PROGRESS,
    REVIEW_PENDING,
    SHIPPING,
    CI_POLLING,
    CI_FAILED,
    DEPLOYED,
    DONE
}
