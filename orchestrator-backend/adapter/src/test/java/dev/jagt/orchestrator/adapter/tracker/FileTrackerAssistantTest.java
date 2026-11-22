package dev.jagt.orchestrator.adapter.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.TrackerAssistant;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.ticket.TicketFacts;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTrackerAssistantTest {

  private static final String LIST =
      """
      # Work

      - [ ] **ABC-42 — A title**
        first body line

        second body line
      - [x] **ABC-7 — Done one**
      - [ ] a line without a key
        stray text
      - [ ] **XYZ-9 — Last**
      ## Later
      - [ ] **ABC-8 — Under a heading**
      """;

  @TempDir Path dir;

  private final TrackerAssistant elsewhere = mock(TrackerAssistant.class);
  private final ConfigService configService = mock(ConfigService.class);
  private final FileTrackerAssistant assistant = new FileTrackerAssistant(elsewhere, configService);

  private Path file;

  @BeforeEach
  void holdTheList() throws IOException {
    file = dir.resolve("work.md");
    Files.writeString(file, LIST);
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults().withTracker(TrackerConfig.defaults().withFile(file.toString())));
  }

  @Test
  void listsEveryKeyInFileOrder() throws IOException {
    assertThat(assistant.findCandidates("").facts())
        .contains(List.of("ABC-42", "ABC-7", "XYZ-9", "ABC-8"));
  }

  @Test
  void readsAnUncheckedItemAsOpenWithFileLink() throws IOException {
    TicketFacts facts = assistant.readTicket("ABC-42").facts().orElseThrow();

    assertThat(facts.key()).isEqualTo("ABC-42");
    assertThat(facts.title()).isEqualTo("A title");
    assertThat(facts.trackerStatus()).isEqualTo("open");
    assertThat(facts.url()).isEqualTo("file://" + file + "#ABC-42");
    assertThat(facts.usable()).isTrue();
  }

  @Test
  void readsCheckedItemAsDone() throws IOException {
    assertThat(assistant.readTicket("ABC-7").facts().orElseThrow().trackerStatus())
        .isEqualTo("done");
  }

  @Test
  void costsNothingToRead() throws IOException {
    assertThat(assistant.readTicket("ABC-42").usage()).isEqualTo(TokenUsage.NONE);
  }

  @Test
  void readsTheTitleAndBodyVerbatimUpToTheNextItem() throws IOException {
    assertThat(assistant.readTicketText("ABC-42").facts())
        .contains("A title\n  first body line\n\n  second body line");
  }

  @Test
  void endsBodyAtHeading() throws IOException {
    assertThat(assistant.readTicketText("XYZ-9").facts()).contains("Last");
  }

  @Test
  void doesNotTakeLineWithoutKeyForAnItem() throws IOException {
    assertThat(assistant.findCandidates("").facts().orElseThrow())
        .doesNotContain("a line without a key");
  }

  @Test
  void asksTheDelegateAboutKeyTheFileDoesNotHold() throws IOException {
    Answer<TicketFacts> other = Answer.unavailable();
    when(elsewhere.readTicket("QQQ-1", List.of())).thenReturn(other);

    assertThat(assistant.readTicket("QQQ-1")).isSameAs(other);
  }

  @Test
  void asksTheDelegateAboutUrlWithoutOpeningTheFile() {
    Answer<TicketFacts> other = Answer.unavailable();
    when(elsewhere.readTicket("https://tracker/ABC-42", List.of())).thenReturn(other);

    assertThat(assistant.readTicket("https://tracker/ABC-42")).isSameAs(other);
    verifyNoInteractions(configService);
  }

  @Test
  void asksTheDelegateForTheTextOfKeyTheFileDoesNotHold() throws IOException {
    when(elsewhere.readTicketText("QQQ-1"))
        .thenReturn(new Answer<>(Optional.of("elsewhere"), TokenUsage.NONE));

    assistant.readTicketText("QQQ-1");

    verify(elsewhere).readTicketText("QQQ-1");
  }

  @Test
  void isUnavailableRatherThanEmptyWhenTheFileIsMissing() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(
                    TrackerConfig.defaults().withFile(dir.resolve("nothing.md").toString())));

    assertThat(assistant.findCandidates("").facts()).isEmpty();
    assertThat(assistant.readTicket("ABC-42").facts()).isEmpty();
    assertThat(assistant.readTicketText("ABC-42").facts()).isEmpty();
  }

  @Test
  void answersAnEmptyListWhenTheFileHoldsNoItem() throws IOException {
    Files.writeString(file, "# Nothing here\n");

    assertThat(assistant.findCandidates("").facts()).contains(List.of());
  }
}
