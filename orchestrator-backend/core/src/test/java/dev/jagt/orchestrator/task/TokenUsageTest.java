package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageTest {

    @Test
    void doesNotCountACallThatNeverReachedAModel() {
        assertThat(TokenUsage.ofCall(0, 0, 0, 0)).isEqualTo(TokenUsage.NONE);
    }

    @ParameterizedTest
    @CsvSource({"1,0,0", "0,1,0", "0,0,1"})
    void countsACallAsSoonAsAnyTokenWasSpent(long input, long cached, long output) {
        assertThat(TokenUsage.ofCall(input, cached, output, 0).calls()).isEqualTo(1);
    }

    @Test
    void addsUpEveryDimensionOfRepeatedCalls() {
        TokenUsage total = TokenUsage.ofCall(25_000, 100, 170, 0.05)
                .plus(TokenUsage.ofCall(26_000, 400, 130, 0.06));

        assertThat(total.calls()).isEqualTo(2);
        assertThat(total.inputTokens()).isEqualTo(51_000);
        assertThat(total.cachedInputTokens()).isEqualTo(500);
        assertThat(total.outputTokens()).isEqualTo(300);
        assertThat(total.total()).isEqualTo(51_800);
    }
}
