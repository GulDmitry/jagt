package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerAssistant;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TicketTextsTest {

  @TempDir Path root;

  private final TrackerAssistant assistant = mock(TrackerAssistant.class);
  private final UsageTracker usage = mock(UsageTracker.class);

  @Test
  void holdsTheTextOfATicketAsReadAndChargesTheReadToItsTask() {
    TicketTexts texts = new TicketTexts(assistant, usage, new KeptTicketTexts(paths()));
    TokenUsage spent = TokenUsage.ofCall(9_000, 0, 80, 0.02);
    when(assistant.readTicketText("ABC-42"))
        .thenReturn(new Answer<>(Optional.of("Widget layout is off"), spent));

    texts.read("ABC-42", "ABC-42", 1_000L);

    assertThat(texts.of("ABC-42")).contains("Widget layout is off");
    verify(usage).chargeTask("ABC-42", spent);
  }

  @Test
  void holdsNoTextForATaskNobodyReadAndAnswersNoReadSince() {
    TicketTexts texts = new TicketTexts(assistant, usage, new KeptTicketTexts(paths()));

    assertThat(texts.of("ABC-42")).isEmpty();
    assertThat(texts.readSince("ABC-42", 0L)).isFalse();
  }

  @Test
  void saysWhetherAFailedReadWasMadeAtOrAfterAMoment() {
    TicketTexts texts = new TicketTexts(assistant, usage, new KeptTicketTexts(paths()));
    when(assistant.readTicketText("ABC-42")).thenReturn(Answer.unavailable());

    texts.read("ABC-42", "ABC-42", 1_000L);

    assertThat(texts.readSince("ABC-42", 1_000L)).isTrue();
    assertThat(texts.readSince("ABC-42", 1_001L)).isFalse();
  }

  @Test
  void keepsAReadTextForEveryLaterStintAndAfterARestart() {
    when(assistant.readTicketText("ABC-42"))
        .thenReturn(new Answer<>(Optional.of("Widget layout is off"), TokenUsage.NONE));
    new TicketTexts(assistant, usage, new KeptTicketTexts(paths())).read("ABC-42", "ABC-42", 1_000L);

    TicketTexts restarted = new TicketTexts(assistant, usage, new KeptTicketTexts(paths()));

    assertThat(restarted.readSince("ABC-42", 9_000L)).isTrue();
    assertThat(restarted.of("ABC-42")).contains("Widget layout is off");
  }

  @Test
  void answersNoTextWhereTheKeepingFolderCannotBeMade() throws java.io.IOException {
    java.nio.file.Files.writeString(root.resolve("ticket-texts"), "in the way");
    TicketTexts texts = new TicketTexts(assistant, usage, new KeptTicketTexts(paths()));
    when(assistant.readTicketText("ABC-42"))
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
