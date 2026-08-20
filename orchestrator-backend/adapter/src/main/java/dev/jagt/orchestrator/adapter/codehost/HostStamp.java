package dev.jagt.orchestrator.adapter.codehost;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** A host's own ISO-8601 timestamp as epoch millis, or 0 when it is absent or unparseable. */
final class HostStamp {

    private HostStamp() {
    }

    static long epochMillis(String iso) {
        if (iso == null || iso.isBlank()) {
            return 0;
        }
        try {
            return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }
}
