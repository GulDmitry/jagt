package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class UsageLimitsPropertiesTest {

  @Test
  void readsTheLimitsEveryHalfHourAndAlertsAtEightyAndNinetyFivePercentWhereNothingIsSet() {
    UsageLimitsProperties limits =
        new Binder(new MapConfigurationPropertySource(Map.of()))
            .bindOrCreate("orchestrator.usage-limits", UsageLimitsProperties.class);

    assertThat(limits.enabled()).isTrue();
    assertThat(limits.every()).isEqualTo(Duration.ofMinutes(30));
    assertThat(limits.thresholds()).containsExactly(80, 95);
    assertThat(limits.paidThresholds()).containsExactly(80, 95);
    assertThat(limits.wakeOnReset()).isFalse();
  }

  @Test
  void keepsWhatTheHumanSet() {
    UsageLimitsProperties limits =
        new UsageLimitsProperties(false, Duration.ofMinutes(10), List.of(50), List.of(60), true);

    assertThat(limits.enabled()).isFalse();
    assertThat(limits.every()).isEqualTo(Duration.ofMinutes(10));
    assertThat(limits.thresholds()).containsExactly(50);
    assertThat(limits.paidThresholds()).containsExactly(60);
    assertThat(limits.wakeOnReset()).isTrue();
  }
}
