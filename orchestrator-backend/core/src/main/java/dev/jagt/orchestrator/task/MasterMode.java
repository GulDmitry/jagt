package dev.jagt.orchestrator.task;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How much of a human's reading the Master session does. ONE setting rather than an on/off beside a mode: "is it
 * running" and "what may it do" are the same question asked twice.
 */
public enum MasterMode {

    /** Nothing is started. The default, and what every suite runs with. */
    OFF,
    /** It reads what a task produced and writes what it found. It issues no verb. */
    JUDGE,
    /** It also presses the button a human would have pressed. */
    ACT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(MasterMode::id).toList();
    }

    /** Empty where the word is not one of these; a caller refuses rather than guessing which was meant. */
    public static Optional<MasterMode> of(String mode) {
        return mode == null || mode.isBlank()
                ? Optional.of(OFF)
                : Arrays.stream(values()).filter(value -> value.id().equals(mode.strip().toLowerCase(Locale.ROOT)))
                        .findFirst();
    }
}
