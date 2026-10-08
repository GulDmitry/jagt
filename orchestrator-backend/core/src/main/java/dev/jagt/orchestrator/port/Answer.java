package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TokenUsage;

import java.util.Optional;
import java.util.function.Function;

/**
 * One question to a model plus what it cost. Empty {@code facts} = unavailable or failed; the usage is reported all
 * the same, a call that failed having been paid for.
 */
public record Answer<T>(Optional<T> facts, TokenUsage usage) {

    /** Never happened, so it cost nothing. */
    public static <T> Answer<T> unavailable() {
        return new Answer<>(Optional.empty(), TokenUsage.NONE);
    }

    /** Shapes the facts while carrying the cost through untouched. */
    public <R> Answer<R> map(Function<? super T, ? extends R> mapper) {
        return new Answer<>(facts.map(mapper), usage);
    }
}
