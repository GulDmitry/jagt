package dev.jagt.orchestrator.flow;

public enum TaskStatus {
    NEW("starting"),
    IN_PROGRESS("agent working"),
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

    /**
     * A round is out with the reviewers, so only the code host moves it on. NOT what the unattended poll watches,
     * which is an open request whatever the status.
     */
    public boolean outForReview() {
        return this == CI_POLLING || this == REVIEWED;
    }
}
