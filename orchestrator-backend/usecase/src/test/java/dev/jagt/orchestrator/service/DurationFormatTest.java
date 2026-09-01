package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DurationFormatTest {

    @Test
    void countsASubMinuteWaitInSecondsBecauseZeroMinutesWouldReadAsNow() {
        assertThat(DurationFormat.countdown(45_000)).isEqualTo("45s");
    }

    @Test
    void roundsAWaitUpSoItNeverReadsShorterThanItIs() {
        assertThat(DurationFormat.countdown(599_999)).isEqualTo("10m");
        assertThat(DurationFormat.countdown(61 * 60_000L)).isEqualTo("2h");
    }

    @Test
    void countsAnElapsedAgeDownToTheMinuteItHasActuallyReached() {
        assertThat(DurationFormat.compact(599_999)).isEqualTo("9m");
    }
}
