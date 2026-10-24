package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhyTest {

    @Test
    void acceptsAReasonOfExactlyTheLongestAllowedLength() {
        assertThat(Why.violation("x".repeat(120))).isEmpty();
    }

    @Test
    void refusesAReasonOneCharacterPastTheLimit() {
        assertThat(Why.violation("x".repeat(121))).isPresent();
    }

    @Test
    void refusesAMissingReason() {
        assertThat(Why.violation(null)).isPresent();
    }
}
