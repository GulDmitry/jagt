package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsageLimitsPropertiesTest {

    @Test
    void readsTheLimitsEveryHalfHourAndAlertsAtEightyAndNinetyFivePercentWhereNothingIsSet() {
        UsageLimitsProperties limits = new UsageLimitsProperties(null, null, null, null, null);

        assertThat(limits.enabled()).isTrue();
        assertThat(limits.every()).isEqualTo(Duration.ofMinutes(30));
        assertThat(limits.thresholds()).containsExactly(80, 95);
        assertThat(limits.paidThresholds()).containsExactly(80, 95);
        assertThat(limits.wakeOnReset()).isFalse();
    }

    @Test
    void keepsWhatTheHumanSet() {
        UsageLimitsProperties limits = new UsageLimitsProperties(false, Duration.ofMinutes(10), List.of(50),
                List.of(60), true);

        assertThat(limits.enabled()).isFalse();
        assertThat(limits.every()).isEqualTo(Duration.ofMinutes(10));
        assertThat(limits.thresholds()).containsExactly(50);
        assertThat(limits.paidThresholds()).containsExactly(60);
        assertThat(limits.wakeOnReset()).isTrue();
    }
}
