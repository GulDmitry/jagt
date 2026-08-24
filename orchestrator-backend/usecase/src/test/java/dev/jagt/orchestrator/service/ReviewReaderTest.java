package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewReaderTest {

    private final MeteredAssistant assistant = mock(MeteredAssistant.class);

    @Test
    void answersTheRoundWithWhatTheReadCameBackWith() {
        when(assistant.readReview("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(
                new Answer<>(Optional.of(new ReviewFacts(true, false, "running", List.of())), TokenUsage.NONE));

        var facts = new ReviewReader(assistant)
                .read("ABC-1", "https://other.example.com/g/p/-/merge_requests/7");

        assertThat(facts).contains(new ReviewFacts(true, false, "running", List.of()));
    }

    @Test
    void answersAnOpenRequestWithWhatTheReadCameBackWith() {
        when(assistant.readMergeRequest("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(
                new Answer<>(Optional.of(new MergeRequestFacts(true, "ABC-1", "main", "t")), TokenUsage.NONE));

        var read = new ReviewReader(assistant)
                .readRequest("https://other.example.com/g/p/-/merge_requests/7");

        assertThat(read.facts()).contains(new MergeRequestFacts(true, "ABC-1", "main", "t"));
    }

    @Test
    void asksWhichMcpServersAreDownWhenAPaidReadDeniesTheRequest() {
        when(assistant.readMergeRequest("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(
                new Answer<>(Optional.of(new MergeRequestFacts(false, "", "", "")), TokenUsage.NONE));

        new ReviewReader(assistant).readRequest("https://other.example.com/g/p/-/merge_requests/7");

        verify(assistant).brokenMcpServers();
    }

    @Test
    void doesNotProbeTheMcpServersForARequestItReadFine() {
        when(assistant.readMergeRequest("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(
                new Answer<>(Optional.of(new MergeRequestFacts(true, "ABC-42", "main", "Excel export")),
                        TokenUsage.NONE));

        new ReviewReader(assistant).readRequest("https://other.example.com/g/p/-/merge_requests/7");

        verify(assistant, never()).brokenMcpServers();
    }

    @Test
    void chargesAReadToTheTaskEvenWhenItCameBackEmpty() {
        TokenUsage spent = TokenUsage.ofCall(26_000, 0, 120, 0.06);
        when(assistant.readReview("https://other.example.com/g/p/-/merge_requests/7"))
                .thenReturn(new Answer<>(Optional.empty(), spent));

        new ReviewReader(assistant).read("ABC-1", "https://other.example.com/g/p/-/merge_requests/7");

        verify(assistant).chargeTask("ABC-1", spent);
    }
}
