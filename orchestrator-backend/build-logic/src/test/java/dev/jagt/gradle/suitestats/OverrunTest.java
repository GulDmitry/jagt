package dev.jagt.gradle.suitestats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OverrunTest {

  @Test
  void suitePastCeilingWarnsWithMinutesLoadAndPower() {
    assertThat(Overrun.line(":roleEval", 11 * 60 + 5, 10, 3.2, "battery"))
        .contains(
            "WARN suite.overrun suite=:roleEval minutes=12 ceiling=10 load=3.2 power=battery");
  }

  @Test
  void suiteAtCeilingSaysNothing() {
    assertThat(Overrun.line(":test", 600, 10, 1.0, "ac")).isEmpty();
  }

  @Test
  void powerIsAcWhenTheReportDrawsFromAcPower() {
    assertThat(Overrun.power("Now drawing from 'AC Power'\n -InternalBattery-0")).isEqualTo("ac");
  }

  @Test
  void powerIsBatteryWhenTheReportDrawsFromBattery() {
    assertThat(Overrun.power("Now drawing from 'Battery Power'")).isEqualTo("battery");
  }

  @Test
  void powerIsUnknownWhenThereIsNoReport() {
    assertThat(Overrun.power("")).isEqualTo("unknown");
  }

  @Test
  void gateTaskWithCeilingIsTracked() {
    assertThat(Overrun.tracked(":roleEval", List.of(":test"), List.of(":roleEval"))).isTrue();
  }

  @Test
  void taskWithoutTimingOrCeilingIsNotTracked() {
    assertThat(Overrun.tracked(":compileJava", List.of(":test"), List.of(":roleEval"))).isFalse();
  }
}
