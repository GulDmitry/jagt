package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.assistant.TicketReader;
import dev.jagt.orchestrator.task.ticket.TicketFacts;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NamedTicketsTest {

  private final TicketReader tickets = mock(TicketReader.class);
  private final TrackerWorkflow workflow = mock(TrackerWorkflow.class);
  private final NamedTickets named = new NamedTickets(tickets, workflow);

  @Test
  void reportsTheStatusTheTrackerGivesForNamedTicket() {
    when(workflow.isItemKey("ABC-7")).thenReturn(true);
    TicketFacts done =
        new TicketFacts(true, "ABC-7", "title", "ABC", List.of(), "https://t/ABC-7", "Done", "");
    when(tickets.read("ABC-7")).thenReturn(new Answer<>(Optional.of(done), TokenUsage.NONE));
    when(workflow.closesWork(done)).thenReturn(true);

    List<NamedTickets.Fact> facts = named.of("ABC-1", "Is ABC-7 closed?");

    assertThat(facts).containsExactly(new NamedTickets.Fact("ABC-7", "Done", true));
  }

  @Test
  void marksTicketUnreadWhereTheTrackerCouldNotBeReached() {
    when(workflow.isItemKey("ABC-7")).thenReturn(true);
    when(tickets.read("ABC-7")).thenReturn(Answer.failed(TokenUsage.NONE, "down"));

    List<NamedTickets.Fact> facts = named.of("ABC-1", "Is ABC-7 closed?");

    assertThat(facts).extracting(NamedTickets.Fact::read).containsExactly(false);
  }

  @Test
  void dropsWordTheTrackerSaysIsNoItem() {
    when(workflow.isItemKey("UTF-8")).thenReturn(true);
    when(tickets.read("UTF-8"))
        .thenReturn(new Answer<>(Optional.of(TicketFacts.defaults()), TokenUsage.NONE));

    List<NamedTickets.Fact> facts = named.of("ABC-1", "keep UTF-8?");

    assertThat(facts).isEmpty();
  }

  @Test
  void chargesTheReadToTheTaskThatAsked() {
    when(workflow.isItemKey("ABC-7")).thenReturn(true);
    when(tickets.read("ABC-7")).thenReturn(Answer.failed(TokenUsage.NONE, "down"));

    named.of("ABC-1", "Is ABC-7 closed?");

    verify(tickets).charge("ABC-1", TokenUsage.NONE);
  }

  @Test
  void readsNothingForTheTasksOwnTicket() {
    when(workflow.isItemKey("ABC-1")).thenReturn(true);

    List<NamedTickets.Fact> facts = named.of("ABC-1", "Is ABC-1 ready?");

    assertThat(facts).isEmpty();
    verify(tickets, never()).read("ABC-1");
  }

  @Test
  void marksEveryTicketPastTheMostUnreadWithoutReadingIt() {
    when(workflow.isItemKey("ABC-2")).thenReturn(true);
    when(workflow.isItemKey("ABC-3")).thenReturn(true);
    when(workflow.isItemKey("ABC-4")).thenReturn(true);
    when(workflow.isItemKey("ABC-5")).thenReturn(true);
    when(workflow.isItemKey("ABC-6")).thenReturn(true);
    when(workflow.isItemKey("ABC-7")).thenReturn(true);
    when(tickets.read("ABC-2"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new TicketFacts(
                        true, "ABC-2", "title", "ABC", List.of(), "https://t/ABC-2", "Open", "")),
                TokenUsage.NONE));
    when(tickets.read("ABC-3"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new TicketFacts(
                        true, "ABC-3", "title", "ABC", List.of(), "https://t/ABC-3", "Open", "")),
                TokenUsage.NONE));
    when(tickets.read("ABC-4"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new TicketFacts(
                        true, "ABC-4", "title", "ABC", List.of(), "https://t/ABC-4", "Open", "")),
                TokenUsage.NONE));
    when(tickets.read("ABC-5"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new TicketFacts(
                        true, "ABC-5", "title", "ABC", List.of(), "https://t/ABC-5", "Open", "")),
                TokenUsage.NONE));
    when(tickets.read("ABC-6"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new TicketFacts(
                        true, "ABC-6", "title", "ABC", List.of(), "https://t/ABC-6", "Open", "")),
                TokenUsage.NONE));

    List<NamedTickets.Fact> facts = named.of("ABC-1", "ABC-2 ABC-3 ABC-4 ABC-5 ABC-6 ABC-7");

    assertThat(facts.getLast()).isEqualTo(new NamedTickets.Fact("ABC-7", "", false));
    verify(tickets, never()).read("ABC-7");
  }
}
