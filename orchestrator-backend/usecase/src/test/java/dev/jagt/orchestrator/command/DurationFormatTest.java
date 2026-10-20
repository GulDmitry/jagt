package dev.jagt.orchestrator.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource({"59999,59s", "60000,1m", "3540000,59m", "3600000,1h"})
    void switchesACountdownToTheNextUnitAtTheExactBoundary(long millis, String expected) {
        assertThat(DurationFormat.countdown(millis)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"3540000,59m", "3600000,1h", "82800000,23h", "86400000,1d"})
    void switchesAnAgeToTheNextUnitAtTheExactBoundary(long millis, String expected) {
        assertThat(DurationFormat.compact(millis)).isEqualTo(expected);
    }
}
