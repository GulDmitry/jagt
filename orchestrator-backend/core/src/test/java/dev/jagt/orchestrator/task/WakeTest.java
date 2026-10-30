package dev.jagt.orchestrator.task;

import dev.jagt.orchestrator.task.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WakeTest {

    @Test
    void readsBackTheMarkItWrote() {
        Wake wake = new Wake(7, 1_000, Step.WAIT_OVER);

        assertThat(Wake.parse(wake.mark())).contains(wake);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1000", "a:b:wake", "7:1000:"})
    void readsNoWakeFromAMarkInAnyOtherShape(String mark) {
        assertThat(Wake.parse(mark)).isEqualTo(Optional.empty());
    }
}
