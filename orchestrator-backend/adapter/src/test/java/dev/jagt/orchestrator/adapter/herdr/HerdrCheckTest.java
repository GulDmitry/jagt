package dev.jagt.orchestrator.adapter.herdr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.port.host.Problem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HerdrCheckTest {

  @Test
  void namesTheKeyAndTheMissingBinaryWhereHerdrIsChosenAndNotInstalled() {
    List<Problem> problems = new HerdrCheck(new HerdrProperties("no-such-herdr", "")).problems();

    assertThat(problems)
        .singleElement()
        .satisfies(problem -> {
          assertThat(problem.key()).isEqualTo("orchestrator.herdr.command");
          assertThat(problem.text()).contains("orchestrator.session-host", "no-such-herdr");
        });
  }

  @Test
  void stripsTheConfiguredHome() {
    assertThat(new HerdrProperties("herdr", " /state/herdr ").home()).isEqualTo("/state/herdr");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/", "..", "/a/../herdr", "/state/other", "state/herdr", "~"})
  void refusesHomeHerdrCannotAddress(String home) {
    assertThatThrownBy(() -> new HerdrProperties("herdr", home))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keepsHerdrAsTheBinaryWhereNoneIsConfigured() {
    assertThat(new HerdrProperties("  ", "").command()).isEqualTo("herdr");
  }
}
