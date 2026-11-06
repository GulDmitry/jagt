package dev.jagt.orchestrator.command;

import org.junit.jupiter.api.Test;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TokenFormatTest {

    @ParameterizedTest
    @CsvSource({
            "0,0",
            "1000,1.0k",
            "9950,10k",
            "999500,1.0M",
            "9950000,10M",
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

    @Test
    void writesACostWithAnUnpricedCallAsALowerBound() {
        assertThat(TokenFormat.cost(TokenUsage.ofCall(500, 0, 10, 1.25).plus(TokenUsage.ofUnpricedCall(500, 0, 10))))
                .isEqualTo("≥1.25");
    }

    @Test
    void writesAFullyPricedCostAsAPlainFigure() {
        assertThat(TokenFormat.cost(TokenUsage.ofCall(500, 0, 10, 1.25))).isEqualTo("1.25");
    }
}
