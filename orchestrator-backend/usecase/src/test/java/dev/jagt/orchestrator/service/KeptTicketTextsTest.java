package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeptTicketTextsTest {

  @TempDir Path root;

  @Test
  void forgetsTheReadOfATaskSoItsTicketIsReadAgain() {
    KeptTicketTexts kept = new KeptTicketTexts(paths());
    kept.markRead("ABC-42", 1_000L);

    kept.forget("ABC-42");

    assertThat(kept.readSince("ABC-42", 0L)).isFalse();
  }

  @Test
  void forgetsAKeptTextFromDisk() {
    KeptTicketTexts kept = new KeptTicketTexts(paths());
    kept.keep("ABC-42", "Widget layout is off");

    kept.forget("ABC-42");

    assertThat(kept.of("ABC-42")).isEmpty();
  }

  private OrchestratorPaths paths() {
    return new OrchestratorPaths(
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString()));
  }
}
