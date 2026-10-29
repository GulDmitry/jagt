package dev.jagt.orchestrator.task;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A moment as a human reads a clock, always in UTC. */
public final class UtcTime {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private UtcTime() {
    }

    public static String clock(long epochMillis) {
        return CLOCK.format(Instant.ofEpochMilli(epochMillis)) + " UTC";
    }
}
