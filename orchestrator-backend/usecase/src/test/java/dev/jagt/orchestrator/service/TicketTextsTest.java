package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TicketTextsTest {

  @TempDir Path root;

  private final Assistants assistants = mock(Assistants.class, RETURNS_DEEP_STUBS);
  private final UsageTracker usage = mock(UsageTracker.class);

  @Test
  void holdsTheTextOfTicketAsReadAndChargesTheReadToItsTask() {
    KeptTicketTexts kept = new KeptTicketTexts(orchestratorPaths().withRoot(root).build());
    TicketTexts texts = new TicketTexts(assistants, usage, kept);
    TokenUsage spent = TokenUsage.ofCall(9_000, 0, 80, 0.02);
    when(assistants.tracker().readTicketText("ABC-42"))
        .thenReturn(new Answer<>(Optional.of("Widget layout is off"), spent));

    texts.read("ABC-42", "ABC-42", 1_000L);

    assertThat(texts.of("ABC-42")).contains("Widget layout is off");
    verify(usage).chargeTask("ABC-42", spent);
  }

  @Test
  void holdsNoTextForTaskNobodyReadAndAnswersNoReadSince() {
    KeptTicketTexts kept = new KeptTicketTexts(orchestratorPaths().withRoot(root).build());
    TicketTexts texts = new TicketTexts(assistants, usage, kept);

    assertThat(texts.of("ABC-42")).isEmpty();
    assertThat(texts.readSince("ABC-42", 0L)).isFalse();
  }

  @Test
  void saysWhetherFailedReadWasMadeAtOrAfterMoment() {
    KeptTicketTexts kept = new KeptTicketTexts(orchestratorPaths().withRoot(root).build());
    TicketTexts texts = new TicketTexts(assistants, usage, kept);
    when(assistants.tracker().readTicketText("ABC-42")).thenReturn(Answer.unavailable());

    texts.read("ABC-42", "ABC-42", 1_000L);

    assertThat(texts.readSince("ABC-42", 1_000L)).isTrue();
    assertThat(texts.readSince("ABC-42", 1_001L)).isFalse();
  }

  @Test
  void keepsReadTextForEveryLaterStintAndAfterRestart() {
    KeptTicketTexts kept = new KeptTicketTexts(orchestratorPaths().withRoot(root).build());
    when(assistants.tracker().readTicketText("ABC-42"))
        .thenReturn(new Answer<>(Optional.of("Widget layout is off"), TokenUsage.NONE));
    new TicketTexts(assistants, usage, kept).read("ABC-42", "ABC-42", 1_000L);

    TicketTexts restarted =
        new TicketTexts(
            assistants, usage, new KeptTicketTexts(orchestratorPaths().withRoot(root).build()));

    assertThat(restarted.readSince("ABC-42", 9_000L)).isTrue();
    assertThat(restarted.of("ABC-42")).contains("Widget layout is off");
  }

  @Test
  void answersNoTextWhereTheKeepingFolderCannotBeMade() throws java.io.IOException {
    KeptTicketTexts kept = new KeptTicketTexts(orchestratorPaths().withRoot(root).build());
    java.nio.file.Files.writeString(root.resolve("ticket-texts"), "in the way");
    TicketTexts texts = new TicketTexts(assistants, usage, kept);
    when(assistants.tracker().readTicketText("ABC-42"))
        .thenReturn(new Answer<>(Optional.of("Widget layout is off"), TokenUsage.NONE));

    texts.read("ABC-42", "ABC-42", 1_000L);

    assertThat(texts.of("ABC-42")).isEmpty();
    assertThat(texts.readSince("ABC-42", 1_000L)).isTrue();
  }
}
