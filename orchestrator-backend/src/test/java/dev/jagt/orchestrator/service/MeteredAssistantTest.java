package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant;
import dev.jagt.orchestrator.assistant.MasterAssistant.Answer;
import dev.jagt.orchestrator.assistant.MasterAssistant.MergeRequestFacts;
import dev.jagt.orchestrator.model.TicketFacts;
import dev.jagt.orchestrator.model.AssistantCallKind;
import dev.jagt.orchestrator.model.ReviewFacts;
import dev.jagt.orchestrator.model.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeteredAssistantTest {

    private final MasterAssistant port = mock(MasterAssistant.class);
    private final UsageTracker usageTracker = mock(UsageTracker.class);
    private final MeteredAssistant metered = new MeteredAssistant(port, usageTracker);

    @Test
    void booksATicketReadUnderItsOwnKind() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 170, 0.05);
        when(port.readTicket("ABC-1")).thenReturn(new Answer<>(
                Optional.of(new TicketFacts(true, "ABC-1", "t", "ABC", List.of(), "")), spent));

        var answer = metered.readTicket("ABC-1");

        assertThat(answer.facts()).isPresent();
        verify(usageTracker).record(AssistantCallKind.TICKET_READ, spent);
    }

    @Test
    void booksAMergeRequestReadUnderItsOwnKind() {
        TokenUsage spent = TokenUsage.ofCall(24_000, 0, 150, 0.05);
        when(port.readMergeRequest("http://mr/1")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, "ABC-1", "main", "group/proj", "title")), spent));

        metered.readMergeRequest("http://mr/1");

        verify(usageTracker).record(AssistantCallKind.MR_READ, spent);
    }

    @Test
    void booksAReviewSweepUnderItsOwnKindEvenWhenTheReadCameBackEmpty() {
        // The kind must be recorded for a FAILED read too: the call was paid for, and this is the kind that
        // repeats up to hourly for a day, so dropping its failures would understate the category that matters.
        TokenUsage spent = TokenUsage.ofCall(31_000, 0, 40, 0.06);
        when(port.readReview("http://mr/1")).thenReturn(new Answer<ReviewFacts>(Optional.empty(), spent));

        var answer = metered.readReview("http://mr/1");

        assertThat(answer.facts()).isEmpty();
        verify(usageTracker).record(AssistantCallKind.REVIEW_SWEEP, spent);
    }
}
