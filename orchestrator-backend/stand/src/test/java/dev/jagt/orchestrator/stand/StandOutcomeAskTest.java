package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StandOutcomeAskTest {

  @ParameterizedTest
  @CsvSource({"YOU,true,false", "CI,false,true", "AGENT,false,false"})
  void theOwnerNamesWhoHasTheTask(String owner, boolean withTheHuman, boolean withTheCodeHost) {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            owner,
            Duration.ofSeconds(1),
            List.of(),
            Path.of("root"),
            StandOutcome.Ending.REACHED,
            "",
            new StandOutcome.Signals("", "", 0));

    assertThat(outcome.withTheHuman()).isEqualTo(withTheHuman);
    assertThat(outcome.withTheCodeHost()).isEqualTo(withTheCodeHost);
  }

  @ParameterizedTest
  @CsvSource({"QUESTION,true", "PERMISSION,true", "DONE,false"})
  void questionOrPermissionReportMeansTheAgentAsked(String report, boolean asked) {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            "YOU",
            Duration.ofSeconds(1),
            List.of(),
            Path.of("root"),
            StandOutcome.Ending.REACHED,
            "",
            new StandOutcome.Signals("", report, 0));

    assertThat(outcome.asked()).isEqualTo(asked);
  }

  @ParameterizedTest
  @CsvSource({"STOPPED,true", "REACHED,false"})
  void onlyStoppedEndingIsStopped(StandOutcome.Ending ending, boolean stopped) {
    StandOutcome outcome =
        new StandOutcome(
            "IN_PROGRESS",
            "YOU",
            Duration.ofSeconds(1),
            List.of(),
            Path.of("root"),
            ending,
            "",
            new StandOutcome.Signals("", "", 0));

    assertThat(outcome.stopped()).isEqualTo(stopped);
  }
}
