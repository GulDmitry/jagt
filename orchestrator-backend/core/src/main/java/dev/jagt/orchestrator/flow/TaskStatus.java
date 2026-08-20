package dev.jagt.orchestrator.flow;

public enum TaskStatus {
    NEW("new"),
    IN_PROGRESS("agent working"),
    REVIEW_PENDING("not shipped"),
    SHIPPING("shipping"),
    CI_POLLING("out for review"),
    CI_FAILED("checks failed"),
    REVIEWED("review clear"),
    APPROVED("approved"),
    DEPLOY_CONFLICT("deploy conflict"),
    DEPLOYED("deployed"),
    REVERTED("reverted"),
    DONE("done");

    /**
     * The enum name is the wire value and what {@code state.json} carries; this is the same status in words that
     * need no glossary. Written ONCE so a status cannot be spelled two ways, and it names a STATE rather than a
     * next move — the highlighted action already gives that.
     */
    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
