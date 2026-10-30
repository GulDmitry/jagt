package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** A step due to be said to a session at {@code at}, for as long as the turn {@code turn} is still the one standing. */
public record Wake(long turn, long at, Step step) {

    /** The key a task's turn holds its pending wake under. */
    public static final String MARK = "wake-at";

    private static final String SEPARATOR = ":";

    /** The wake a mark holds, empty for a mark in any other shape. */
    public static Optional<Wake> parse(@Nullable String mark) {
        String[] parts = mark == null ? new String[0] : mark.split(SEPARATOR, 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Step.find(parts[2]).map(step -> new Wake(Long.parseLong(parts[0]), Long.parseLong(parts[1]), step));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public String mark() {
        return turn + SEPARATOR + at + SEPARATOR + step.id();
    }
}
