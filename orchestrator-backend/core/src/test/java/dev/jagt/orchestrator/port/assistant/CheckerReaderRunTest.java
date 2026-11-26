package dev.jagt.orchestrator.port.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CheckerReaderRunTest {

  private final CheckerReader.Run run =
      new CheckerReader.Run(
          "",
          "check it",
          Path.of("/wt"),
          "abc@1",
          "",
          new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
          "{}",
          "checker");

  @Test
  void asksTheSameReadWhereNothingWasWrong() {
    assertThat(run.correcting(List.of())).isSameAs(run);
  }

  @Test
  void tellsTheReadWhatTheLastAnswerGotWrong() {
    CheckerReader.Run again = run.correcting(List.of("status of \"it greets\": one of met"));

    assertThat(again.prompt()).startsWith("check it").contains("it greets");
    assertThat(again.sha()).isEqualTo("abc@1");
  }
}
