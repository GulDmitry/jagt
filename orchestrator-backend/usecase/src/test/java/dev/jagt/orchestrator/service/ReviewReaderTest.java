package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.CodeHost;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewReaderTest {

    private final CodeHost codeHost = mock(CodeHost.class);
    private final MeteredAssistant assistant = mock(MeteredAssistant.class);

    @Test
    void readsOverTheCodeHostWithoutSpendingATokenWhenItClaimsTheUrl() {
        when(codeHost.supports("https://git.example.com/g/p/-/merge_requests/7")).thenReturn(true);
        when(codeHost.readReview("https://git.example.com/g/p/-/merge_requests/7"))
                .thenReturn(Optional.of(new ReviewFacts(true, true, "success", List.of())));

        var facts = new ReviewReader(List.of(codeHost), assistant)
                .read("ABC-1", "https://git.example.com/g/p/-/merge_requests/7");

        assertThat(facts).contains(new ReviewFacts(true, true, "success", List.of()));
        verifyNoInteractions(assistant);
    }

    @Test
    void fallsBackToTheHeadlessReadWhenNoHostClaimsTheUrl() {
        when(codeHost.supports("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(false);
        when(assistant.readReview("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(
                new Answer<>(Optional.of(new ReviewFacts(true, false, "running", List.of())), TokenUsage.NONE));

        var facts = new ReviewReader(List.of(codeHost), assistant)
                .read("ABC-1", "https://other.example.com/g/p/-/merge_requests/7");

        assertThat(facts).contains(new ReviewFacts(true, false, "running", List.of()));
    }

    @Test
    void asksWhichMcpServersAreDownWhenAPaidReadDeniesTheRequest() {
        when(assistant.readMergeRequest("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(
                new Answer<>(Optional.of(new MergeRequestFacts(false, "", "", "")), TokenUsage.NONE));

        new ReviewReader(List.of(), assistant).readRequest("https://other.example.com/g/p/-/merge_requests/7");

        verify(assistant).brokenMcpServers();
    }

    @Test
    void doesNotProbeTheMcpServersForARequestItReadFine() {
        when(assistant.readMergeRequest("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(
                new Answer<>(Optional.of(new MergeRequestFacts(true, "ABC-42", "main", "Excel export")),
                        TokenUsage.NONE));

        new ReviewReader(List.of(), assistant).readRequest("https://other.example.com/g/p/-/merge_requests/7");

        verify(assistant, never()).brokenMcpServers();
    }

    @Test
    void chargesAHeadlessReadToTheTaskEvenWhenItCameBackEmpty() {
        TokenUsage spent = TokenUsage.ofCall(26_000, 0, 120, 0.06);
        when(assistant.readReview("https://other.example.com/g/p/-/merge_requests/7"))
                .thenReturn(new Answer<>(Optional.empty(), spent));

        new ReviewReader(List.of(), assistant)
                .read("ABC-1", "https://other.example.com/g/p/-/merge_requests/7");

        verify(assistant).chargeTask("ABC-1", spent);
    }

    @Test
    void neverPaysForAHeadlessReadWhenTheClaimingHostFailedToRead() {
        when(codeHost.supports("https://git.example.com/g/p/-/merge_requests/7")).thenReturn(true);
        when(codeHost.readReview("https://git.example.com/g/p/-/merge_requests/7")).thenReturn(Optional.empty());

        var facts = new ReviewReader(List.of(codeHost), assistant)
                .read("ABC-1", "https://git.example.com/g/p/-/merge_requests/7");

        assertThat(facts).isEmpty();
        verifyNoInteractions(assistant);
    }

    @Test
    void readsAnOpenRequestOverTheCodeHostWithoutSpendingATokenWhenItClaimsTheUrl() {
        when(codeHost.supports("https://git.example.com/g/p/-/merge_requests/7")).thenReturn(true);
        when(codeHost.readRequest("https://git.example.com/g/p/-/merge_requests/7"))
                .thenReturn(Optional.of(new MergeRequestFacts(true, "ABC-1", "main", "ABC-1 Widget layout")));

        var read = new ReviewReader(List.of(codeHost), assistant)
                .readRequest("https://git.example.com/g/p/-/merge_requests/7");

        assertThat(read.facts()).contains(new MergeRequestFacts(true, "ABC-1", "main", "ABC-1 Widget layout"));
        verifyNoInteractions(assistant);
    }

    @Test
    void fallsBackToTheHeadlessReadOfARequestOnAHostNobodyConfigured() {
        when(codeHost.supports("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(false);
        when(assistant.readMergeRequest("https://other.example.com/g/p/-/merge_requests/7")).thenReturn(
                new Answer<>(Optional.of(new MergeRequestFacts(true, "ABC-1", "main", "t")), TokenUsage.NONE));

        var read = new ReviewReader(List.of(codeHost), assistant)
                .readRequest("https://other.example.com/g/p/-/merge_requests/7");

        assertThat(read.facts()).contains(new MergeRequestFacts(true, "ABC-1", "main", "t"));
    }

    @Test
    void neverPaysForARequestReadWhenTheClaimingHostFailedToReadIt() {
        when(codeHost.supports("https://git.example.com/g/p/-/merge_requests/7")).thenReturn(true);
        when(codeHost.readRequest("https://git.example.com/g/p/-/merge_requests/7"))
                .thenReturn(Optional.empty());

        var read = new ReviewReader(List.of(codeHost), assistant)
                .readRequest("https://git.example.com/g/p/-/merge_requests/7");

        assertThat(read.facts()).isEmpty();
        verifyNoInteractions(assistant);
    }
}
