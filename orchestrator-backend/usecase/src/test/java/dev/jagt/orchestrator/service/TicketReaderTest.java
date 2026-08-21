package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.port.Tracker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TicketReaderTest {

    private final Tracker tracker = mock(Tracker.class);
    private final MeteredAssistant assistant = mock(MeteredAssistant.class);

    @Test
    void readsATicketOverTheConfiguredTrackerForFree() {
        TicketFacts facts = new TicketFacts(true, "ABC-42", "Widget layout is off", "ABC", List.of(),
                "https://tracker/ABC-42");
        when(tracker.supports("ABC-42")).thenReturn(true);
        when(tracker.readTicket("ABC-42")).thenReturn(Optional.of(facts));

        var read = new TicketReader(List.of(tracker), assistant).read("ABC-42");

        assertThat(read.facts()).contains(facts);
        assertThat(read.usage()).isEqualTo(TokenUsage.NONE);
        verifyNoInteractions(assistant);
    }

    @Test
    void paysForAReadOfSomethingNoConfiguredTrackerCanFetch() {
        Answer<TicketFacts> paid = new Answer<>(Optional.of(
                new TicketFacts(true, "ABC-42", "t", "ABC", List.of(), "https://elsewhere.example.com/item/9")),
                TokenUsage.ofCall(25_000, 0, 120, 0.05));
        when(tracker.supports(anyString())).thenReturn(false);
        when(assistant.readTicket("https://elsewhere.example.com/item/9")).thenReturn(paid);

        var read = new TicketReader(List.of(tracker), assistant).read("https://elsewhere.example.com/item/9");

        assertThat(read).isEqualTo(paid);
    }

    /**
     * The tracker that CLAIMED the reference owns it: paying a model to try the same read again would spend
     * money invisibly on every expired token, and the launch would look healthy while nothing was configured
     * correctly.
     */
    @Test
    void doesNotPayForASecondAttemptWhenTheTrackerItselfFailed() {
        when(tracker.supports("ABC-42")).thenReturn(true);
        when(tracker.readTicket("ABC-42")).thenReturn(Optional.empty());

        var read = new TicketReader(List.of(tracker), assistant).read("ABC-42");

        assertThat(read.facts()).isEmpty();
        verifyNoInteractions(assistant);
    }

    /**
     * A model that never found its tracker tool reports the same "does not exist" as an item that is gone, so a
     * launch believed the first denial and the task kept no title at all.
     */
    @Test
    void readsTheTicketOnASecondAttemptWhenTheModelFirstSaidItDoesNotExist() {
        TicketFacts read = new TicketFacts(true, "ABC-42", "Widget layout is off", "ABC", List.of(),
                "https://tracker/ABC-42");
        when(tracker.supports(anyString())).thenReturn(false);
        when(assistant.readTicket("ABC-42"))
                .thenReturn(new Answer<>(Optional.of(new TicketFacts(false, "", "", "", List.of(), "")),
                        TokenUsage.ofCall(10, 0, 1, 0.5)))
                .thenReturn(new Answer<>(Optional.of(read), TokenUsage.ofCall(20, 0, 2, 0.25)));

        var answer = new TicketReader(List.of(tracker), assistant, 3, Duration.ZERO).read("ABC-42");

        assertThat(answer.facts()).contains(read);
        assertThat(answer.usage()).isEqualTo(new TokenUsage(2, 30, 0, 3, 0.75));
    }

    @Test
    void givesUpOnTheAttemptLimitAndKeepsWhatTheLastReadSaid() {
        Answer<TicketFacts> denial = new Answer<>(
                Optional.of(new TicketFacts(false, "", "", "", List.of(), "")), TokenUsage.NONE);
        when(tracker.supports(anyString())).thenReturn(false);
        when(assistant.readTicket("ABC-42")).thenReturn(denial);

        var answer = new TicketReader(List.of(tracker), assistant, 3, Duration.ZERO).read("ABC-42");

        assertThat(answer.facts()).contains(new TicketFacts(false, "", "", "", List.of(), ""));
        verify(assistant, times(3)).readTicket("ABC-42");
    }

    @Test
    void chargesWhatAReadCostToTheTaskItNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 120, 0.05);

        new TicketReader(List.of(), assistant).charge("ABC-42", spent);

        verify(assistant).chargeTask("ABC-42", spent);
    }
}
