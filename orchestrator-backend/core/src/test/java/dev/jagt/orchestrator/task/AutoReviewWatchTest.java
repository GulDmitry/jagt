package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoReviewWatchTest {

    @Test
    void sayingPollingIsOffForTheTaskIsAStoppedPollWithItsOwnChip() {
        AutoReviewWatch watch = AutoReviewWatch.offForTask();

        assertThat(watch.state()).isEqualTo(AutoReviewWatch.State.OFF_FOR_TASK);
        assertThat(watch.stopped()).isTrue();
        assertThat(watch.label()).isEqualTo("polling off");
    }

    @Test
    void sayingTheRoundWasNeverStampedIsAStoppedPollThatCannotBeTimed() {
        AutoReviewWatch watch = AutoReviewWatch.noRound();

        assertThat(watch.state()).isEqualTo(AutoReviewWatch.State.NO_ROUND);
        assertThat(watch.stopped()).isTrue();
        assertThat(watch.label()).isEqualTo("cannot time this");
    }
}
