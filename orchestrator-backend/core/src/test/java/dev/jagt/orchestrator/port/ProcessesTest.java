package dev.jagt.orchestrator.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProcessesTest {

  @Test
  void handsBackTheSameResultWhenTheCommandSucceeded() {
    Processes.Result result = new Processes.Result(0, "out", "");

    assertThat(result.expectSuccess("build")).isSameAs(result);
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {
        "2,partial,boom,build failed (exit 2): boom",
        "1,only out,'  ',build failed (exit 1): only out"
      })
  void namesTheActionAndWhatTheFailedCommandActuallySaid(
      int exit, String stdout, String stderr, String message) {
    assertThatThrownBy(() -> new Processes.Result(exit, stdout, stderr).expectSuccess("build"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(message);
  }
}
