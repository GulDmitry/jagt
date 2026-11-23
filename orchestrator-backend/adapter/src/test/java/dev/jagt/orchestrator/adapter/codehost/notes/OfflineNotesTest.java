package dev.jagt.orchestrator.adapter.codehost.notes;

import static dev.jagt.orchestrator.adapter.codehost.TestHostSettingsBuilder.hostSettings;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfflineNotesTest {

  private final OfflineNotes offline = new OfflineNotes();
  private final HostSettings host = hostSettings().withKey("orchestrator.codeHost").build();

  @Test
  void noRequestAwaitsReviewWhereTheHostIsNoneAndThatIsNoFailure() {
    Answer<List<ReviewRequest>> answer = offline.awaitingReview(host, "git@h:a/b.git");

    assertThat(answer.facts()).contains(List.of());
    assertThat(answer.failure()).isEmpty();
  }

  @Test
  void hostWithNoReactionsCannotSayAndIsNeverZero() {
    Answer<Map<String, Reaction>> answer = offline.reactions(host, "u", List.of("1"));

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).isEmpty();
  }

  @Test
  void noNoteIsPostedWhereNoHostIsReached() {
    Answer<String> answer = offline.post(host, "u", new NoteDraft("x", ""));

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).startsWith("orchestrator.codeHost");
  }

  @Test
  void answersForNoneAndForReplayedRun() {
    assertThat(offline.kinds()).containsExactlyInAnyOrder("none", "replay");
  }
}
