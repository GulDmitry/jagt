package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.AssistantHealth;
import dev.jagt.orchestrator.port.AssistantUnavailable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantBreakerTest {

    private final Clock clock = mock(Clock.class);
    private final AssistantBreaker breaker = new AssistantBreaker(
            AssistantProperties.empty().withBreakerFailures(3).withBreakerCooldown(Duration.ofMinutes(5)), clock);

    @Test
    void staysClosedBelowTheFailureThreshold() {
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");

        assertThatCode(breaker::admit).doesNotThrowAnyException();
        assertThat(breaker.outage()).isEmpty();
    }

    @Test
    void refusesWithTheLastProviderErrorAfterThresholdFailuresInARow() {
        when(clock.millis()).thenReturn(1_000L);
        breaker.failed("502 bad gateway");
        breaker.failed("502 bad gateway");
        breaker.failed("529 overloaded");

        assertThatThrownBy(breaker::admit).isInstanceOf(AssistantUnavailable.class)
                .extracting(refused -> ((AssistantUnavailable) refused).lastCause()).isEqualTo("529 overloaded");
        assertThat(breaker.outage()).contains(new AssistantHealth.Outage("529 overloaded", 301_000L));
    }

    @Test
    void aSuccessBetweenFailuresRestartsTheCount() {
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        breaker.succeeded();
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");

        assertThatCode(breaker::admit).doesNotThrowAnyException();
    }

    @Test
    void refusesUntilTheCooldownHasPassed() {
        when(clock.millis()).thenReturn(0L);
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        when(clock.millis()).thenReturn(299_999L);

        assertThatThrownBy(breaker::admit).isInstanceOf(AssistantUnavailable.class);
    }

    @Test
    void closesWhenTheProbeSucceeds() {
        when(clock.millis()).thenReturn(0L);
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        when(clock.millis()).thenReturn(300_000L);

        breaker.admit();
        breaker.succeeded();

        assertThat(breaker.outage()).isEmpty();
        assertThatCode(breaker::admit).doesNotThrowAnyException();
    }

    @Test
    void reopensForAnotherCooldownWhenTheProbeFails() {
        when(clock.millis()).thenReturn(0L);
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        when(clock.millis()).thenReturn(300_000L);
        breaker.admit();

        breaker.failed("503 unavailable");

        assertThat(breaker.outage()).contains(new AssistantHealth.Outage("503 unavailable", 600_000L));
    }

    @Test
    void refusesASecondReadWhileTheProbeIsInFlight() {
        when(clock.millis()).thenReturn(0L);
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        when(clock.millis()).thenReturn(300_000L);
        breaker.admit();

        assertThatThrownBy(breaker::admit).isInstanceOf(AssistantUnavailable.class);
    }

    @Test
    void anInconclusiveProbeLetsTheNextReadProbeAgain() {
        when(clock.millis()).thenReturn(0L);
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        breaker.failed("529 overloaded");
        when(clock.millis()).thenReturn(300_000L);
        breaker.admit();

        breaker.inconclusive();

        assertThatCode(breaker::admit).doesNotThrowAnyException();
    }
}
