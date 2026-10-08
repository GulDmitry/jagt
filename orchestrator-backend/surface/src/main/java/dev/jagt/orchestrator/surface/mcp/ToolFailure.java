package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.flow.Refusal;

import java.util.Locale;

/** What a failed call tells the agent to do next, read off what the handler threw. */
public enum ToolFailure {

    /** The call itself is wrong: correct it and send it again. */
    VALIDATION(false),
    /** The world refused — a branch held, a conflict, nothing to deploy. The same call will not pass. */
    BUSINESS(false),
    PERMISSION(false),
    TRANSIENT(true);

    private final boolean retryable;

    ToolFailure(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ToolFailure of(Exception thrown) {
        return switch (thrown) {
            case ToolRefusal refusal -> refusal.failure();
            case Refusal _ -> BUSINESS;
            case IllegalArgumentException _ -> VALIDATION;
            case IllegalStateException _ -> BUSINESS;
            default -> TRANSIENT;
        };
    }
}
