package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketReaderTest {

    private final MeteredAssistant assistant = mock(MeteredAssistant.class);

    @Test
    void answersWithTheReadAndWhatItCost() {
        Answer<TicketFacts> paid = new Answer<>(Optional.of(
                new TicketFacts(true, "ABC-42", "t", "ABC", List.of(), "https://elsewhere.example.com/item/9")),
                TokenUsage.ofCall(25_000, 0, 120, 0.05));
        when(assistant.readTicket(eq("https://elsewhere.example.com/item/9"), any())).thenReturn(paid);

        var read = new TicketReader(assistant).read("https://elsewhere.example.com/item/9");

        assertThat(read).isEqualTo(paid);
    }

    @Test
    void readsTheTicketOnASecondAttemptWhenTheModelFirstSaidItDoesNotExist() {
        TicketFacts read = new TicketFacts(true, "ABC-42", "Widget layout is off", "ABC", List.of(),
                "https://tracker/ABC-42");
        when(assistant.readTicket(eq("ABC-42"), any()))
                .thenReturn(new Answer<>(Optional.of(new TicketFacts(false, "", "", "", List.of(), "")),
                        TokenUsage.ofCall(10, 0, 1, 0.5)))
                .thenReturn(new Answer<>(Optional.of(read), TokenUsage.ofCall(20, 0, 2, 0.25)));

        var answer = new TicketReader(assistant, new RetryPolicy(3, Duration.ZERO, Duration.ofMinutes(2)))
                .read("ABC-42");

        assertThat(answer.facts()).contains(read);
        assertThat(answer.usage()).isEqualTo(new TokenUsage(2, 30, 0, 3, 0.75));
    }

    @Test
    void givesUpOnTheAttemptLimitAndKeepsWhatTheLastReadSaid() {
        Answer<TicketFacts> denial = new Answer<>(
                Optional.of(new TicketFacts(false, "", "", "", List.of(), "")), TokenUsage.NONE);
        when(assistant.readTicket(eq("ABC-42"), any())).thenReturn(denial);

        var answer = new TicketReader(assistant, new RetryPolicy(3, Duration.ZERO, Duration.ofMinutes(2)))
                .read("ABC-42");

        assertThat(answer.facts()).contains(new TicketFacts(false, "", "", "", List.of(), ""));
        verify(assistant, times(3)).readTicket(eq("ABC-42"), any());
    }

    @Test
    void tellsTheNextAttemptWhatWasWrongWithTheLastAnswer() {
        when(assistant.readTicket(eq("ABC-42"), any()))
                .thenReturn(new Answer<>(Optional.of(
                        new TicketFacts(true, "ABC-42", "", "ABC", List.of(), "https://tracker/ABC-42")),
                        TokenUsage.NONE))
                .thenReturn(new Answer<>(Optional.of(
                        new TicketFacts(true, "ABC-42", "Widget layout is off", "ABC", List.of(),
                                "https://tracker/ABC-42")), TokenUsage.NONE));
        ArgumentCaptor<List<String>> corrections = ArgumentCaptor.captor();

        new TicketReader(assistant, new RetryPolicy(3, Duration.ZERO, Duration.ofMinutes(2))).read("ABC-42");

        verify(assistant, times(2)).readTicket(eq("ABC-42"), corrections.capture());
        assertThat(corrections.getAllValues().get(0)).isEmpty();
        assertThat(corrections.getAllValues().get(1)).anySatisfy(said -> assertThat(said).contains("title"));
    }

    @Test
    void chargesWhatAReadCostToTheTaskItNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 120, 0.05);

        new TicketReader(assistant).charge("ABC-42", spent);

        verify(assistant).chargeTask("ABC-42", spent);
    }
}
