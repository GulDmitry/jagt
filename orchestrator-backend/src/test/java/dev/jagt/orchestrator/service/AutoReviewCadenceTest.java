package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AutoReviewCadenceTest {

    private final AutoReviewCadence cadence = new AutoReviewCadence(Duration.ofHours(24), 10, 60);

    @Test
    void pollsAtTheTightestIntervalAtTheStartOfTheWindow() {
        assertThat(cadence.pollInterval(Duration.ZERO)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void rampsToTheMaxIntervalByTheEndOfTheWindow() {
        assertThat(cadence.pollInterval(Duration.ofHours(24))).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void escalatesLinearlyAcrossTheWindow() {
        assertThat(cadence.pollInterval(Duration.ofHours(12))).isEqualTo(Duration.ofMinutes(35));
    }

    @Test
    void backsOffMonotonicallyAsTheMrAges() {
        assertThat(cadence.pollInterval(Duration.ofHours(6)))
                .isLessThan(cadence.pollInterval(Duration.ofHours(18)));
    }

    @Test
    void stopsPollingOnceTheWindowHasElapsed() {
        assertThat(cadence.pollInterval(Duration.ofHours(24).plusMinutes(1))).isNull();
    }
}
