package dev.jagt.orchestrator.stand.replay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayLogTest {

  @Test
  void divergenceIsReadWithItsStepAndCauseAndEndsTheReplay(@TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt-backend.log"),
        """
        not json
        {"message":"replay diverged","step":"turn 2","cause":"other question"}
        """);

    ReplayLog log = ReplayLog.read(root);

    assertThat(log.divergences()).containsExactly("turn 2: other question");
    assertThat(log.drained()).isFalse();
    assertThat(log.ended()).isTrue();
  }

  @Test
  void drainedRecordingEndsTheReplayWithoutDivergence(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("jagt-backend.log"), "{\"message\":\"replay drained\"}\n");

    ReplayLog log = ReplayLog.read(root);

    assertThat(log.divergences()).isEmpty();
    assertThat(log.ended()).isTrue();
  }

  @Test
  void replayWithNoLogHasNotEnded(@TempDir Path root) {
    ReplayLog log = ReplayLog.read(root);

    assertThat(log.ended()).isFalse();
  }
}
