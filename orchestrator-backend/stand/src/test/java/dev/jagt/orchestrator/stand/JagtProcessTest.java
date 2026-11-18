package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JagtProcessTest {

  @Test
  void theFirstJagtsOutputAndBackendLogAreKeptBesideTheSecondsBeforeItStarts(@TempDir Path root)
      throws Exception {
    Files.writeString(root.resolve("jagt.out"), "first out");
    Files.writeString(root.resolve("jagt-backend.log"), "first log");

    JagtProcess.keepOutput(root);

    assertThat(root.resolve("jagt.out")).doesNotExist();
    assertThat(root.resolve("jagt-backend.log")).doesNotExist();
    assertThat(root.resolve("jagt.out.before-restart")).hasContent("first out");
    assertThat(root.resolve("jagt-backend.log.before-restart")).hasContent("first log");
  }

  @Test
  void keepsNothingWhenTheFirstJagtWroteNoBackendLog(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("jagt.out"), "first out");

    JagtProcess.keepOutput(root);

    assertThat(root.resolve("jagt-backend.log.before-restart")).doesNotExist();
  }

  @Test
  void theEvidenceOfFailedRunHoldsBothJagtsOutput(@TempDir Path dir) throws Exception {
    Path root = Files.createDirectories(dir.resolve("run"));
    Files.writeString(root.resolve("jagt.out.before-restart"), "first out");
    Files.writeString(root.resolve("jagt.out"), "second out");

    Path kept = StandEvidence.keep(root, "mutation-restart-mid-turn/judge", dir.resolve("failed"));

    assertThat(kept.resolve("jagt.out.before-restart")).hasContent("first out");
    assertThat(kept.resolve("jagt.out")).hasContent("second out");
  }

  @Test
  void stateIsTheFileJagtLeftInItsDataFolder(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("jagt"));
    Files.writeString(root.resolve("jagt/state.json"), "{\"tasks\":{}}");

    JagtProcess jagt =
        new JagtProcess(
            root, 8123, mock(StandBoard.class), (dir, port) -> mock(Process.class), Duration.ZERO);

    String state = jagt.state();

    assertThat(state).isEqualTo("{\"tasks\":{}}");
  }

  @Test
  void stateFailsNamingTheFileWhenJagtLeftNone(@TempDir Path root) {
    JagtProcess jagt =
        new JagtProcess(
            root, 8123, mock(StandBoard.class), (dir, port) -> mock(Process.class), Duration.ZERO);

    assertThatThrownBy(jagt::state)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining(root.resolve("jagt/state.json").toString());
  }
}
