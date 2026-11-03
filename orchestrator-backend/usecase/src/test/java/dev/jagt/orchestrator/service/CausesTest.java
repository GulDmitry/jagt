package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CausesTest {

    @Test
    void aFailureWithABlankMessageIsNamedByItsType() {
        String cause = Causes.of(new IllegalStateException(" "));

        assertThat(cause).isEqualTo("IllegalStateException");
    }
}
