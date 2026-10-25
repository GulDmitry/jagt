package dev.jagt.orchestrator.port;

/** A read refused unasked because the assistant's provider failed repeatedly; never "not found". */
public final class AssistantUnavailable extends IllegalStateException {

    private final String lastCause;

    public AssistantUnavailable(String lastCause) {
        super("assistant unavailable: " + lastCause);
        this.lastCause = lastCause;
    }

    public String lastCause() {
        return lastCause;
    }
}
