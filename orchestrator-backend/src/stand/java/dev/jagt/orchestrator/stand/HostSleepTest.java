package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class HostSleepTest {

    @Test
    void aPollAfterTheLaptopSleptNineHundredSecondsReadsAsSuspended() {
        assertThat(HostSleep.between(Instant.parse("2026-10-25T08:33:00Z"), Instant.parse("2026-10-25T08:48:40Z"))).isTrue();
    }

    @Test
    void aSlowPollOfTenSecondsIsNotASleep() {
        assertThat(HostSleep.between(Instant.parse("2026-10-25T08:33:00Z"), Instant.parse("2026-10-25T08:33:10Z"))).isFalse();
    }
}
