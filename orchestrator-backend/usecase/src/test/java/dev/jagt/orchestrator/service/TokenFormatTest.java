package dev.jagt.orchestrator.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TokenFormatTest {

    @ParameterizedTest
    @CsvSource({
            "0,0",
            "812,812",
            "4800,4.8k",
            "9940,9.9k",
            "9960,10k",
            "63500,64k",
            "999499,999k",
            "999600,1.0M",
            "1200000,1.2M",
            "9940000,9.9M",
            "9994000,10M",
            "12300000,12M"})
    void writesATokenCountShortEnoughForItsColumnWithoutLosingItsScale(long tokens, String expected) {
        assertThat(TokenFormat.compact(tokens)).isEqualTo(expected);
    }
}
