package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewRoundTest {

    private final ReviewReader reader = mock(ReviewReader.class);
    private final PipelineRetry host = mock(PipelineRetry.class);
    private final ReviewRound round = new ReviewRound(reader, host);

    @Test
    void readsTheSameRoundTwiceWhereTheModelWroteOtherNoteIdsButTheHostGivesTheSame() {
        when(reader.read("ABC-1", "http://mr/1"))
                .thenReturn(new Answer<>(Optional.of(new ReviewFacts(true, false, "success", "", List.of("rename x"), 7,
                        List.of(), List.of("101187"), "", false, false)), TokenUsage.NONE))
                .thenReturn(new Answer<>(Optional.of(new ReviewFacts(true, false, "success", "", List.of("rename x"), 7,
                        List.of(), List.of("101219"), "", false, false)), TokenUsage.NONE));
        when(host.unresolvedNotes("proj", "http://mr/1")).thenReturn(new Answer<>(Optional.of(List.of("301")), TokenUsage.NONE));

        ReviewFacts first = round.read("ABC-1", "proj", "http://mr/1").facts().orElseThrow();
        ReviewFacts second = round.read("ABC-1", "proj", "http://mr/1").facts().orElseThrow();

        assertThat(first.noteKey().sameAs(second.noteKey())).isTrue();
        assertThat(first.newestNotes()).containsExactly("301");
    }

    @Test
    void carriesNoNoteIdWhereTheHostCouldNotBeAsked() {
        when(reader.read("ABC-1", "http://mr/1")).thenReturn(new Answer<>(Optional.of(new ReviewFacts(true, false,
                "success", "", List.of("rename x"), 7, List.of(), List.of("101187"), "", false, false)), TokenUsage.NONE));
        when(host.unresolvedNotes("proj", "http://mr/1")).thenReturn(Answer.failed(TokenUsage.NONE, "gh exited 1"));

        ReviewFacts facts = round.read("ABC-1", "proj", "http://mr/1").facts().orElseThrow();

        assertThat(facts.newestNotes()).isEmpty();
    }

    @Test
    void asksTheHostNothingOfARequestTheModelFoundMissing() {
        when(reader.read("ABC-1", "http://mr/1")).thenReturn(new Answer<>(Optional.of(new ReviewFacts(false, false,
                "", "", List.of(), 0, List.of(), List.of(), "", false, false)), TokenUsage.NONE));

        round.read("ABC-1", "proj", "http://mr/1");

        verifyNoInteractions(host);
    }
}
