package dev.jagt.orchestrator.protocol;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** The one sentence a request that sets off a case gives for itself. */
public final class Why {

    public static final String FIELD = "why";
    public static final int MAX = 120;
    public static final String DESCRIBES = "Required: why you ask for this, one sentence of at most " + MAX
            + " characters, kept in the task's history for the human. E.g. \"work done, tests green, ready for"
            + " review\", \"service A calls B's API, the change crosses both\".";

    private Why() {
    }

    public static Optional<Violation> violation(@Nullable String why) {
        if (why == null) {
            return Optional.of(new Violation(FIELD, "required: one sentence of at most " + MAX + " characters"));
        }
        if (why.length() > MAX) {
            return Optional.of(new Violation(FIELD, "at most " + MAX + " characters, not " + why.length()));
        }
        return Optional.empty();
    }
}
