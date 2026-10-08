package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OneLineTest {

    @Test
    void flattensEveryRunOfWhitespaceIntoOneSpace() {
        assertThat(OneLine.of("  fixed\n\tthe   build ", 40)).isEqualTo("fixed the build");
    }

    @Test
    void cutsTextPastTheWidthAndMarksTheCut() {
        assertThat(OneLine.of("fixed the build", 6)).isEqualTo("fixed…");
    }

    @Test
    void keepsAbsentTextAbsent() {
        assertThat(OneLine.of(null, 6)).isNull();
    }
}
