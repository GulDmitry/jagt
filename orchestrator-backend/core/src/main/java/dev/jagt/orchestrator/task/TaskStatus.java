package dev.jagt.orchestrator.task;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TaskStatus {
    NEW("starting"),
    PLAN_PENDING("plan waiting"),
    IN_PROGRESS("agent working"),
    VERIFYING("verifying"),
    REVIEW_PENDING("not shipped"),
    SHIPPING("pushing"),
    CI_POLLING("out for review"),
    CI_FAILED("checks failed"),
    REVIEWED("awaiting approval"),
    APPROVED("approved"),
    DEPLOY_CONFLICT("deploy conflict"),
    DEPLOYED("deployed"),
    REVERTED("reverted"),
    DONE("done");

    /** The enum name is the wire value; this is the same status in words, naming a STATE rather than a next move. */
    private final String label;

    public String label() {
        return label;
    }
}
