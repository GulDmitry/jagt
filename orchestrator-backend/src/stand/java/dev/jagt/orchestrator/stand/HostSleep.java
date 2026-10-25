package dev.jagt.orchestrator.stand;

import java.time.Duration;
import java.time.Instant;

final class HostSleep {

    static final Duration GAP = Duration.ofSeconds(90);

    private HostSleep() {
    }

    static boolean between(Instant before, Instant after) {
        return Duration.between(before, after).compareTo(GAP) > 0;
    }
}
