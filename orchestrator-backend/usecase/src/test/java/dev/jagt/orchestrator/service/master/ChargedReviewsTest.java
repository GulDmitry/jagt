package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.Assistants;
import dev.jagt.orchestrator.service.TrustedGitState;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChargedReviewsTest {

  private final Assistants assistants = mock(Assistants.class, RETURNS_DEEP_STUBS);
  private final UsageTracker usage = mock(UsageTracker.class);
  private final TrustedGitState trusted = mock(TrustedGitState.class);

  @Test
  void chargesTheTaskWhatItsReadSpent() {
    RoundReviewer.Round round = new RoundReviewer.Round("", "prompt", List.of(), "");
    TokenUsage spent = TokenUsage.ofCall(1_000, 0, 10, 0.01);
    when(assistants.reviewer().review(round)).thenReturn(new Answer<>(Optional.empty(), spent));

    new ChargedReviews(assistants, usage, trusted, List::of).review("ABC-1", round);

    verify(usage).chargeTask("ABC-1", spent);
  }

  @Test
  void failsReadThatAnsweredNothing() {
    RoundReviewer.Round round = new RoundReviewer.Round("", "prompt", List.of(), "");
    when(assistants.reviewer().review(round)).thenReturn(Answer.unavailable());

    Judgement judged =
        new ChargedReviews(assistants, usage, trusted, List::of).review("ABC-1", round);

    assertThat(judged.failure()).isEqualTo("the review answered nothing");
  }

  @Test
  void readsNothingWhileWhatTheTasksGitObeysIsNotTrusted() {
    RoundReviewer.Round round = new RoundReviewer.Round("", "prompt", List.of(), "");
    when(trusted.distrust("ABC-1"))
        .thenReturn(Optional.of("core.fsmonitor in /r/.git/config added"));

    Judgement judged =
        new ChargedReviews(assistants, usage, trusted, List::of).review("ABC-1", round);

    assertThat(judged.failure()).isEqualTo("core.fsmonitor in /r/.git/config added");
    verifyNoInteractions(assistants.reviewer());
  }

  @Test
  void readsWhatTheSessionMayWriteBesideItsWorktrees() {
    RoundReviewer.Round round =
        new RoundReviewer.Round("", "prompt", List.of(Path.of("/wt/ABC-1")), "");
    RoundReviewer.Round reaching =
        new RoundReviewer.Round("", "prompt", List.of(Path.of("/wt/ABC-1"), Path.of("/specs")), "");
    when(assistants.reviewer().review(reaching)).thenReturn(Answer.unavailable());

    new ChargedReviews(assistants, usage, trusted, () -> List.of(Path.of("/specs")))
        .review("ABC-1", round);

    verify(assistants.reviewer()).review(reaching);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void saysWhetherTheReaderLoadsAnMcpServerAsTheReaderDoes(boolean loads) {
    when(assistants.reviewer().loadsMcpServer()).thenReturn(loads);

    assertThat(new ChargedReviews(assistants, usage, trusted, List::of).loadsMcpServer())
        .isEqualTo(loads);
  }

  @Test
  void logsEachChargedCallWithItsKind() {
    RoundReviewer.Round round = new RoundReviewer.Round("", "prompt", List.of(), "");
    when(assistants.reviewer().review(round)).thenReturn(Answer.unavailable());
    try (FakeLogCapture log = new FakeLogCapture(ChargedReviews.class)) {

      new ChargedReviews(assistants, usage, trusted, List::of).review("ABC-1", round);

      assertThat(log.events())
          .filteredOn(event -> event.getMessage().equals("master call charged"))
          .flatExtracting(ILoggingEvent::getKeyValuePairs)
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains("kind=MASTER_REVIEW");
    }
  }
}
