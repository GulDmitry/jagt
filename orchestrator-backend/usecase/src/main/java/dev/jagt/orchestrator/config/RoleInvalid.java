package dev.jagt.orchestrator.config;

/** A {@code role.yml} that names a word its vocabulary does not hold, or leaves a state without an exit. */
public final class RoleInvalid extends RuntimeException {

    public RoleInvalid(String reason) {
        super(reason);
    }
}
