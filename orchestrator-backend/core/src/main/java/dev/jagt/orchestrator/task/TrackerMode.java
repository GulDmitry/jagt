package dev.jagt.orchestrator.task;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which ends of the loop the tracker drives. ONE setting rather than a flag per end: taking work in and closing
 * it are two questions, and a configuration able to answer them separately still answers them in one word.
 */
public enum TrackerMode {

    /** Nothing arrives and nothing closes. The default, and what every suite runs with. */
    OFF,
    /** Items at the starting stage become tasks; closing stays yours. */
    TAKE,
    /** Tasks close on the landed stage; nothing arrives unasked. */
    CLOSE,
    BOTH;

    public boolean takes() {
        return this == TAKE || this == BOTH;
    }

    public boolean closes() {
        return this == CLOSE || this == BOTH;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(TrackerMode::id).toList();
    }

    /** Empty where the word is not one of these; a caller refuses rather than guessing which was meant. */
    public static Optional<TrackerMode> of(String mode) {
        return mode == null || mode.isBlank()
                ? Optional.of(OFF)
                : Arrays.stream(values()).filter(value -> value.id().equals(mode.strip().toLowerCase(Locale.ROOT)))
                        .findFirst();
    }
}
