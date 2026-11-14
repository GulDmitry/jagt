package dev.jagt.orchestrator.service.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.protocol.read.RetryPolicy;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.task.launch.TicketFacts;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TicketReaderTest {

  private final Assistants assistants = mock(Assistants.class, RETURNS_DEEP_STUBS);
  private final McpHealth mcp = mock(McpHealth.class);
  private final UsageTracker usage = mock(UsageTracker.class);

  @Test
  void answersWithTheReadAndWhatItCost() {
    Answer<TicketFacts> paid =
        new Answer<>(
            Optional.of(
                TicketFacts.defaults()
                    .withExists(true)
                    .withKey("ABC-42")
                    .withTitle("t")
                    .withTrackerProject("ABC")
                    .withUrl("https://elsewhere.example.com/item/9")),
            TokenUsage.ofCall(25_000, 0, 120, 0.05));
    when(assistants.tracker().readTicket(eq("https://elsewhere.example.com/item/9"), any()))
        .thenReturn(paid);

    var read =
        new TicketReader(assistants, mcp, usage, RetryPolicy.PAID_READ)
            .read("https://elsewhere.example.com/item/9");

    assertThat(read).isEqualTo(paid);
  }

  @Test
  void namesTheServerThatIsDownWhenTheReadGaveNoSentenceOfItsOwn() {
    when(assistants.tracker().readTicket(eq("ABC-42"), any())).thenReturn(Answer.unavailable());
    when(mcp.brokenServers()).thenReturn(Optional.of(List.of("atlassian (needs authentication)")));

    var answer =
        new TicketReader(
                assistants, mcp, usage, new RetryPolicy(1, Duration.ZERO, Duration.ofMinutes(2)))
            .read("ABC-42");

    assertThat(answer.failure()).contains("atlassian (needs authentication)", "installation.md");
  }

  @Test
  void readsTheTicketOnSecondAttemptWhenTheModelFirstSaidItDoesNotExist() {
    TicketFacts read =
        TicketFacts.defaults()
            .withExists(true)
            .withKey("ABC-42")
            .withTitle("Widget layout is off")
            .withTrackerProject("ABC")
            .withUrl("https://tracker/ABC-42");
    when(assistants.tracker().readTicket(eq("ABC-42"), any()))
        .thenReturn(
            new Answer<>(Optional.of(TicketFacts.defaults()), TokenUsage.ofCall(10, 0, 1, 0.5)))
        .thenReturn(new Answer<>(Optional.of(read), TokenUsage.ofCall(20, 0, 2, 0.25)));

    var answer =
        new TicketReader(
                assistants, mcp, usage, new RetryPolicy(3, Duration.ZERO, Duration.ofMinutes(2)))
            .read("ABC-42");

    assertThat(answer.facts()).contains(read);
    assertThat(answer.usage()).isEqualTo(new TokenUsage(2, 30, 0, 3, 0.75));
  }

  @Test
  void givesUpOnTheAttemptLimitAndKeepsWhatTheLastReadSaid() {
    Answer<TicketFacts> denial = new Answer<>(Optional.of(TicketFacts.defaults()), TokenUsage.NONE);
    when(assistants.tracker().readTicket(eq("ABC-42"), any())).thenReturn(denial);

    var answer =
        new TicketReader(
                assistants, mcp, usage, new RetryPolicy(3, Duration.ZERO, Duration.ofMinutes(2)))
            .read("ABC-42");

    assertThat(answer.facts()).contains(TicketFacts.defaults());
    verify(assistants.tracker(), times(3)).readTicket(eq("ABC-42"), any());
  }

  @Test
  void tellsTheNextAttemptWhatWasWrongWithTheLastAnswer() {
    when(assistants.tracker().readTicket(eq("ABC-42"), any()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    TicketFacts.defaults()
                        .withExists(true)
                        .withKey("ABC-42")
                        .withTrackerProject("ABC")
                        .withUrl("https://tracker/ABC-42")),
                TokenUsage.NONE))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    TicketFacts.defaults()
                        .withExists(true)
                        .withKey("ABC-42")
                        .withTitle("Widget layout is off")
                        .withTrackerProject("ABC")
                        .withUrl("https://tracker/ABC-42")),
                TokenUsage.NONE));
    ArgumentCaptor<List<String>> corrections = ArgumentCaptor.captor();

    new TicketReader(
            assistants, mcp, usage, new RetryPolicy(3, Duration.ZERO, Duration.ofMinutes(2)))
        .read("ABC-42");

    verify(assistants.tracker(), times(2)).readTicket(eq("ABC-42"), corrections.capture());
    assertThat(corrections.getAllValues().get(0)).isEmpty();
    assertThat(corrections.getAllValues().get(1))
        .anySatisfy(said -> assertThat(said).contains("title"));
  }

  @Test
  void chargesWhatReadCostToTheTaskItNamed() {
    TokenUsage spent = TokenUsage.ofCall(25_000, 0, 120, 0.05);

    new TicketReader(assistants, mcp, usage, RetryPolicy.PAID_READ).charge("ABC-42", spent);

    verify(usage).chargeTask("ABC-42", spent);
  }
}
