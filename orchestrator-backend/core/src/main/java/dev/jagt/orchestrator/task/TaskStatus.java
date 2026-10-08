package dev.jagt.orchestrator.task;

public enum TaskStatus {
    NEW("starting"),
    PLAN_PENDING("plan waiting"),
    IN_PROGRESS("agent working"),
    VERIFYING("verifying"),
    REVIEW_PENDING("not shipped"),
    SHIPPING("pushing"),
    CI_POLLING("out for review"),
    CI_FAILED("checks failed"),
    REVIEWED("not approved"),
    APPROVED("approved"),
    DEPLOY_CONFLICT("deploy conflict"),
    DEPLOYED("deployed"),
    REVERTED("reverted"),
    DONE("done");

    /** The enum name is the wire value; this is the same status in words, naming a STATE rather than a next move. */
    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
