package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudePricesTest {

    @ParameterizedTest
    @CsvSource({
            "claude-fable-5-1, FABLE_5_1",
            "claude-fable-5, FABLE",
            "claude-opus-5, OPUS",
            "claude-opus-4-1-20250805, OPUS_4_1",
            "claude-sonnet-5, SONNET_5",
            "claude-sonnet-4-6, SONNET",
            "claude-haiku-4-5-20251001, HAIKU",
    })
    void picksTheMostSpecificFamilyTheModelIdNames(String model, ClaudePrices expected) {
        assertThat(ClaudePrices.of(model)).contains(expected);
    }
}
