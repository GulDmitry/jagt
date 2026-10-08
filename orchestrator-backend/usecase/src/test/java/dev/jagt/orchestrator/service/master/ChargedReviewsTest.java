package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChargedReviewsTest {

    private final RoundReviewer reviewer = mock(RoundReviewer.class);
    private final UsageTracker usage = mock(UsageTracker.class);

    @Test
    void chargesTheTaskWhatItsReadSpent() {
        RoundReviewer.Round round = new RoundReviewer.Round("", "prompt", List.of(), "");
        TokenUsage spent = TokenUsage.ofCall(1_000, 0, 10, 0.01);
        when(reviewer.review(round)).thenReturn(new Answer<>(Optional.empty(), spent));

        new ChargedReviews(reviewer, usage).review("ABC-1", round);

        verify(usage).chargeTask("ABC-1", spent);
    }

    @Test
    void failsAReadThatAnsweredNothing() {
        RoundReviewer.Round round = new RoundReviewer.Round("", "prompt", List.of(), "");
        when(reviewer.review(round)).thenReturn(Answer.unavailable());

        Judgement judged = new ChargedReviews(reviewer, usage).review("ABC-1", round);

        assertThat(judged.failure()).isEqualTo("the review answered nothing");
    }
}
