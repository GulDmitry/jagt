package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeptTicketTextsTest {

  @TempDir Path root;

  @Test
  void forgetsTheReadOfTaskSoItsTicketIsReadAgain() {
    KeptTicketTexts kept = new KeptTicketTexts(orchestratorPaths().withRoot(root).build());
    kept.markRead("ABC-42", 1_000L);

    kept.forget("ABC-42");

    assertThat(kept.readSince("ABC-42", 0L)).isFalse();
  }

  @Test
  void forgetsKeptTextFromDisk() {
    KeptTicketTexts kept = new KeptTicketTexts(orchestratorPaths().withRoot(root).build());
    kept.keep("ABC-42", "Widget layout is off");

    kept.forget("ABC-42");

    assertThat(kept.of("ABC-42")).isEmpty();
  }
}
