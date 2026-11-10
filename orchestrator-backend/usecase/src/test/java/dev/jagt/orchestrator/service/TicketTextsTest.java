package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
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
    TicketTexts texts = new TicketTexts(assistants, usage, new KeptTicketTexts(paths()));
    TokenUsage spent = TokenUsage.ofCall(9_000, 0, 80, 0.02);
    when(assistants.tracker().readTicketText("ABC-42"))
        .thenReturn(new Answer<>(Optional.of("Widget layout is off"), spent));

    texts.read("ABC-42", "ABC-42", 1_000L);

    assertThat(texts.of("ABC-42")).contains("Widget layout is off");
    verify(usage).chargeTask("ABC-42", spent);
  }

  @Test
  void holdsNoTextForTaskNobodyReadAndAnswersNoReadSince() {
    TicketTexts texts = new TicketTexts(assistants, usage, new KeptTicketTexts(paths()));

    assertThat(texts.of("ABC-42")).isEmpty();
    assertThat(texts.readSince("ABC-42", 0L)).isFalse();
  }

  @Test
  void saysWhetherFailedReadWasMadeAtOrAfterMoment() {
    TicketTexts texts = new TicketTexts(assistants, usage, new KeptTicketTexts(paths()));
    when(assistants.tracker().readTicketText("ABC-42")).thenReturn(Answer.unavailable());

    texts.read("ABC-42", "ABC-42", 1_000L);

    assertThat(texts.readSince("ABC-42", 1_000L)).isTrue();
    assertThat(texts.readSince("ABC-42", 1_001L)).isFalse();
  }

  @Test
  void keepsReadTextForEveryLaterStintAndAfterRestart() {
    when(assistants.tracker().readTicketText("ABC-42"))
        .thenReturn(new Answer<>(Optional.of("Widget layout is off"), TokenUsage.NONE));
    new TicketTexts(assistants, usage, new KeptTicketTexts(paths()))
        .read("ABC-42", "ABC-42", 1_000L);

    TicketTexts restarted = new TicketTexts(assistants, usage, new KeptTicketTexts(paths()));

    assertThat(restarted.readSince("ABC-42", 9_000L)).isTrue();
    assertThat(restarted.of("ABC-42")).contains("Widget layout is off");
  }

  @Test
  void answersNoTextWhereTheKeepingFolderCannotBeMade() throws java.io.IOException {
    java.nio.file.Files.writeString(root.resolve("ticket-texts"), "in the way");
    TicketTexts texts = new TicketTexts(assistants, usage, new KeptTicketTexts(paths()));
    when(assistants.tracker().readTicketText("ABC-42"))
        .thenReturn(new Answer<>(Optional.of("Widget layout is off"), TokenUsage.NONE));

    texts.read("ABC-42", "ABC-42", 1_000L);

    assertThat(texts.of("ABC-42")).isEmpty();
    assertThat(texts.readSince("ABC-42", 1_000L)).isTrue();
  }

  private OrchestratorPaths paths() {
    return new OrchestratorPaths(
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString()));
  }
}
