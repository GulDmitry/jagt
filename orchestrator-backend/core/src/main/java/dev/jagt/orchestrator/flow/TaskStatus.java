package dev.jagt.orchestrator.flow;

public enum TaskStatus {
    NEW,
    IN_PROGRESS,
    REVIEW_PENDING,
    SHIPPING,
    CI_POLLING,
    CI_FAILED,
    REVIEWED,
    APPROVED,
    DEPLOY_CONFLICT,
    DEPLOYED,
    REVERTED,
    DONE
}
