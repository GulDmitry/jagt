package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @ParameterizedTest
    @CsvSource({"1,false", "2,false", "3,true", "4,true"})
    void callsAnAttemptTheLastOnceItReachesTheNumberOfAttemptsAllowed(int attempt, boolean last) {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofMinutes(1));

        assertThat(policy.lastAttempt(attempt)).isEqualTo(last);
    }

    @Test
    void allowsAPolicyOfASingleAttempt() {
        assertThat(new RetryPolicy(1, Duration.ZERO, Duration.ofMinutes(1)).lastAttempt(1)).isTrue();
    }

    @Test
    void refusesAPolicyThatSendsNothing() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ZERO, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A policy that sends nothing is not a retry policy");
    }
}
