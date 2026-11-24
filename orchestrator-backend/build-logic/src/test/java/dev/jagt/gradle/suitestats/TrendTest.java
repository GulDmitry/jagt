package dev.jagt.gradle.suitestats;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrendTest {

  private static final BigDecimal FACTOR = new BigDecimal("1.5");

  @Test
  void freshRunSlowerThanFreshMedianIsNamed() {
    List<Map<String, Object>> history =
        List.of(
            Map.of(
                "task", ":test", "command", "test",
                "success", true, "fresh", true, "seconds", 100));

    assertThat(Trend.slower(history, ":test", "test", 200, true, 10, FACTOR))
        .contains("suiteStats: :test took 200 s, its trend 100 s over 1 fresh runs");
  }

  @Test
  void runThatIsNotFreshIsNeverDegradation() {
    List<Map<String, Object>> history =
        List.of(
            Map.of(
                "task", ":test", "command", "test",
                "success", true, "fresh", true, "seconds", 100));

    assertThat(Trend.slower(history, ":test", "test", 900, false, 10, FACTOR)).isEmpty();
  }

  @Test
  void earlierRunsThatWereNotFreshMakeNoTrend() {
    List<Map<String, Object>> history =
        List.of(
            Map.of(
                "task", ":test", "command", "test",
                "success", true, "fresh", false, "seconds", 100));

    assertThat(Trend.slower(history, ":test", "test", 900, true, 10, FACTOR)).isEmpty();
  }

  @Test
  void freshRunWithinFactorSaysNothing() {
    List<Map<String, Object>> history =
        List.of(
            Map.of(
                "task", ":test", "command", "test",
                "success", true, "fresh", true, "seconds", 100));

    assertThat(Trend.slower(history, ":test", "test", 150, true, 10, FACTOR)).isEmpty();
  }
}
