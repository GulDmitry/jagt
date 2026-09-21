package dev.jagt.orchestrator.protocol;

import java.time.Duration;

/**
 * How often a message may be sent again before a human has to look. A violation is a correction, so the same
 * request goes out again carrying it; a sender that has not produced a valid message in this many tries will not
 * on the next one, and the outcome that must never happen is nobody being told.
 */
public record RetryPolicy(int attempts, Duration between, Duration budget) {

    /**
     * A read a model answers, every attempt of which is paid for. The budget bounds a launch a human is waiting
     * on: a read that hangs to its own timeout leaves no room for another.
     */
    public static final RetryPolicy PAID_READ = new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofMinutes(2));

    public RetryPolicy {
        if (attempts < 1) {
            throw new IllegalArgumentException("A policy that sends nothing is not a retry policy");
        }
    }

    public boolean lastAttempt(int attempt) {
        return attempt >= attempts;
    }
}
