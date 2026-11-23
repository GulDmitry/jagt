package dev.jagt.orchestrator.adapter.codehost.notes;

import static dev.jagt.orchestrator.adapter.codehost.TestHostSettingsBuilder.hostSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitLabNotesTest {

  private static final String REQUEST = "https://git.corp.invalid/acme/shop/-/merge_requests/7";

  private final Processes processes = mock(Processes.class);
  private final GitLabNotes gitlab = new GitLabNotes(processes, new ReachLedger());
  private final HostSettings host = hostSettings().build();

  @Test
  void rateLimitIsFailureInTheHostsWordsNeverAnEmptyList() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "glab: 429 Retry later (rate limit)"));

    Answer<List<RequestNote>> answer = gitlab.notes(host, REQUEST);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).contains("rate limit");
  }

  @Test
  void signedOutCliIsFailureWithLostReach() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "glab: 401 Unauthorized"));

    Answer<List<ReviewRequest>> answer =
        gitlab.awaitingReview(host, "git@git.corp.invalid:acme/shop.git");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.reach()).isPresent();
  }

  @Test
  void hostThatIsDownIsFailureWithLostReach() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "dial tcp 10.0.0.1:443: connection refused"));

    Answer<RequestChange> answer = gitlab.change(host, REQUEST);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.reach()).isPresent();
  }

  @Test
  void ownRequestsAreLeftOutOfThoseAwaitingReview() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(0, "{\"username\":\"jagt-bot\"}", ""),
            new Processes.Result(
                0,
                "[{\"web_url\":\"https://git.corp.invalid/acme/shop/-/merge_requests/7\",\"title\":\"Add cart\","
                    + "\"author\":{\"username\":\"ada\"},\"sha\":\"0123abc\"},"
                    + "{\"web_url\":\"https://git.corp.invalid/acme/shop/-/merge_requests/8\","
                    + "\"title\":\"Mine\",\"author\":{\"username\":\"jagt-bot\"},\"sha\":\"def\"}]",
                ""));

    Answer<List<ReviewRequest>> answer =
        gitlab.awaitingReview(host, "git@git.corp.invalid:acme/shop.git");

    assertThat(answer.facts().get()).extracting(ReviewRequest::author).containsExactly("ada");
  }

  @Test
  void systemNotesAreLeftOutAndResolutionComesFromTheThread() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(0, "{\"username\":\"jagt-bot\"}", ""),
            new Processes.Result(
                0,
                """
                [{"id":"d1","notes":[
                  {"id":31,"body":"Null here","system":false,"resolvable":true,"resolved":false,
                   "author":{"username":"scanner","bot":true},"position":{"new_path":"A.java"}},
                  {"id":32,"body":"Fixed","system":false,"resolvable":true,"resolved":false,
                   "author":{"username":"jagt-bot"}}]},
                 {"id":"d2","notes":[
                  {"id":33,"body":"assigned","system":true,"author":{"username":"ada"}}]}]
                """,
                ""));

    Answer<List<RequestNote>> answer = gitlab.notes(host, REQUEST);

    assertThat(answer.facts().get())
        .extracting(RequestNote::id, RequestNote::kind, RequestNote::resolution)
        .containsExactly(
            tuple("31", AuthorKind.BOT, Resolution.OPEN),
            tuple("32", AuthorKind.ACTOR, Resolution.OPEN));
  }

  @Test
  void replyGoesToTheDiscussionHoldingTheNote() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(0, "[{\"id\":\"d1\",\"notes\":[{\"id\":31}]}]", ""),
            new Processes.Result(0, "{\"id\":77}", ""));

    Answer<String> answer = gitlab.post(host, REQUEST, new NoteDraft("Done", "31"));

    assertThat(answer.facts()).contains("77");
  }

  @Test
  void replyToNoteTheRequestDoesNotHoldIsFailure() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[{\"id\":\"d1\",\"notes\":[{\"id\":31}]}]", ""));

    Answer<String> answer = gitlab.post(host, REQUEST, new NoteDraft("Done", "99"));

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).isEqualTo("no note 99 on the request");
  }

  @Test
  void thumbsAreCountedPerNote() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"name\":\"thumbsup\"},{\"name\":\"thumbsdown\"},{\"name\":\"thumbsdown\"},"
                    + "{\"name\":\"tada\"}]",
                ""));

    Answer<Map<String, Reaction>> answer = gitlab.reactions(host, REQUEST, List.of("31"));

    assertThat(answer.facts().get()).containsEntry("31", new Reaction(1, 2));
  }
}
