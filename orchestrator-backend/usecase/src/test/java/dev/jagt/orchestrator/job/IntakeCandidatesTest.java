package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.Assistants;
import dev.jagt.orchestrator.service.TicketReader;
import dev.jagt.orchestrator.task.launch.TicketFacts;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntakeCandidatesTest {

  private final Assistants assistants = mock(Assistants.class, RETURNS_DEEP_STUBS);
  private final TicketReader tickets = mock(TicketReader.class);
  private final TrackerWorkflow workflow = mock(TrackerWorkflow.class);
  private final IntakeCandidates candidates = new IntakeCandidates(assistants, tickets, workflow);

  @Test
  void answersNothingAtAllWhenTheSearchNeverReachedTheTracker() {
    when(workflow.candidateQuery()).thenReturn("whatever is ready");
    when(assistants.tracker().findCandidates("whatever is ready")).thenReturn(Answer.unavailable());

    assertThat(candidates.waiting(Set.of())).isEmpty();
  }

  @Test
  void warnsRatherThanErrsWhenPollNeverReachedTheTracker() {
    when(workflow.candidateQuery()).thenReturn("whatever is ready");
    when(assistants.tracker().findCandidates("whatever is ready")).thenReturn(Answer.unavailable());
    try (FakeLogCapture log = new FakeLogCapture(IntakeCandidates.class)) {
      candidates.waiting(Set.of());

      assertThat(log.events()).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
    }
  }

  @Test
  void buysNoReadForAnItemJagtAlreadyHolds() {
    when(workflow.candidateQuery()).thenReturn("whatever is ready");
    when(assistants.tracker().findCandidates("whatever is ready"))
        .thenReturn(new Answer<>(Optional.of(List.of("ABC-42")), TokenUsage.NONE));

    candidates.waiting(Set.of("ABC-42"));

    verify(tickets, never()).read(anyString());
  }

  @Test
  void buysNoReadForSomethingTheSearchAnsweredThatIsNotAnIssueKey() {
    when(workflow.candidateQuery()).thenReturn("whatever is ready");
    when(assistants.tracker().findCandidates("whatever is ready"))
        .thenReturn(new Answer<>(Optional.of(List.of("I could not find any")), TokenUsage.NONE));

    candidates.waiting(Set.of());

    verify(tickets, never()).read(anyString());
  }

  @Test
  void asksTheWorkflowNothingAboutAnItemWhoseStageNobodyCouldRead() {
    when(workflow.candidateQuery()).thenReturn("whatever is ready");
    when(assistants.tracker().findCandidates("whatever is ready"))
        .thenReturn(new Answer<>(Optional.of(List.of("ABC-42")), TokenUsage.NONE));
    when(tickets.read("ABC-42"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    TicketFacts.defaults()
                        .withExists(true)
                        .withKey("ABC-42")
                        .withTitle("Widget layout is off")
                        .withUrl("https://tracker/ABC-42")),
                TokenUsage.NONE));

    assertThat(candidates.waiting(Set.of())).contains(List.of());
    verify(workflow, never()).startsWork(any());
  }

  @Test
  void offersTheItemTheWorkflowSaysMachineMayStart() {
    when(workflow.isItemKey("ABC-42")).thenReturn(true);
    TicketFacts item =
        TicketFacts.defaults()
            .withExists(true)
            .withKey("ABC-42")
            .withTitle("Widget layout is off")
            .withUrl("https://tracker/ABC-42")
            .withTrackerStatus("In Progress")
            .withAssignee("jdoe");
    when(workflow.candidateQuery()).thenReturn("whatever is ready");
    when(assistants.tracker().findCandidates("whatever is ready"))
        .thenReturn(new Answer<>(Optional.of(List.of("ABC-42")), TokenUsage.NONE));
    when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
    when(workflow.startsWork(item)).thenReturn(true);

    assertThat(candidates.waiting(Set.of()))
        .contains(List.of(new IntakeCandidates.Ready(item, TokenUsage.NONE)));
  }

  @Test
  void buysNoSixthReadWithinOnePoll() {
    when(workflow.candidateQuery()).thenReturn("whatever is ready");
    when(assistants.tracker().findCandidates("whatever is ready"))
        .thenReturn(
            new Answer<>(
                Optional.of(List.of("ABC-1", "ABC-2", "ABC-3", "ABC-4", "ABC-5", "ABC-6")),
                TokenUsage.NONE));
    when(tickets.read(anyString())).thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));

    candidates.waiting(Set.of());

    verify(tickets, never()).read("ABC-6");
  }
}
