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

    @ParameterizedTest
    @CsvSource({"500,0,1,0", "500,9000,0,1", "0,9000,0,1"})
    void callsAStartColdOnlyWhenItHadInputAndReadNothingFromTheCache(long input, long cached, int cold, int warm) {
        TokenUsage start = TokenUsage.ofCall(input, cached, 10, 0).asStart();

        assertThat(start.coldStarts()).isEqualTo(cold);
        assertThat(start.warmStarts()).isEqualTo(warm);
    }

    @Test
    void leavesACallWithoutCacheFieldsOutOfBothStartCounts() {
        TokenUsage total = TokenUsage.ofCall(500, 0, 10, 0).asStart().plus(TokenUsage.ofCall(500, 0, 10, 0));

        assertThat(total.calls()).isEqualTo(2);
        assertThat(total.measuredStarts()).isEqualTo(1);
    }

    @Test
    void addsUpCostAndStartCountsOfRepeatedCalls() {
        TokenUsage total = TokenUsage.ofCall(500, 0, 10, 0.25).asStart()
                .plus(TokenUsage.ofCall(500, 9_000, 10, 0.5).asStart())
                .plus(TokenUsage.ofCall(500, 0, 10, 0.25).asStart());

        assertThat(total.costUsd()).isEqualTo(1.0);
        assertThat(total.coldStarts()).isEqualTo(2);
        assertThat(total.warmStarts()).isEqualTo(1);
        assertThat(total.measuredStarts()).isEqualTo(3);
    }

    @Test
    void marksNothingAsAStartWhenNoCallWasMade() {
        assertThat(TokenUsage.NONE.asStart()).isEqualTo(TokenUsage.NONE);
    }

    @Test
    void callsAStartWarmWhenItSpentOnlyOutputTokens() {
        TokenUsage start = TokenUsage.ofCall(0, 0, 10, 0).asStart();

        assertThat(start.coldStarts()).isZero();
        assertThat(start.warmStarts()).isEqualTo(1);
    }
}
