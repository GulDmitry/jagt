package dev.jagt.orchestrator.adapter.codehost.notes;

import static dev.jagt.orchestrator.adapter.codehost.TestHostSettingsBuilder.hostSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.NoteAnchor;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GitHubNotesTest {

  private static final String REQUEST = "https://ghe.corp.invalid/acme/shop/pull/7";

  private final Processes processes = mock(Processes.class);
  private final GitHubNotes github = new GitHubNotes(processes, new ReachLedger());
  private final HostSettings host = hostSettings().build();

  @Test
  void rateLimitIsFailureInTheHostsWordsNeverAnEmptyList() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                1, "", "gh: API rate limit exceeded for user ID 42. If you reach out to GitHub"));

    Answer<List<RequestNote>> answer = github.notes(host, REQUEST);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).contains("rate limit exceeded");
  }

  @Test
  void signedOutCliIsFailureWithLostReach() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                4, "", "To get started with GitHub CLI, please run:  gh auth login"));

    Answer<List<ReviewRequest>> answer =
        github.awaitingReview(host, "git@ghe.corp.invalid:acme/shop.git");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.reach()).isPresent();
  }

  @Test
  void hostThatIsDownIsFailureWithLostReach() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                1, "", "error connecting: dial tcp: lookup ghe.corp.invalid: no such host"));

    Answer<List<RequestNote>> answer = github.notes(host, REQUEST);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.reach()).isPresent();
  }

  @Test
  void requestsNamedForReviewAreReadWithTheirAuthorAndHead() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"url\":\"https://ghe.corp.invalid/acme/shop/pull/7\",\"title\":\"Add cart\","
                    + "\"author\":{\"login\":\"ada\"},\"headRefOid\":\"0123abc\"}]",
                ""));

    Answer<List<ReviewRequest>> answer =
        github.awaitingReview(host, "git@ghe.corp.invalid:acme/shop.git");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence("pr", "list", "--search", "review-requested:@me", "-R")
        .contains("ghe.corp.invalid/acme/shop");
    assertThat(answer.facts())
        .contains(
            List.of(
                new ReviewRequest(
                    "https://ghe.corp.invalid/acme/shop/pull/7", "Add cart", "ada", "0123abc")));
  }

  @Test
  void noteIsToldApartAsTheActorsTheHostsBotOrPerson() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"data":{"viewer":{"login":"jagt-bot"},"repository":{"pullRequest":{
                 "comments":{"nodes":[
                  {"databaseId":11,"body":"Looks fine","author":{"login":"ada","__typename":"User"}}]},
                 "reviewThreads":{"nodes":[
                  {"id":"T1","isResolved":true,"comments":{"nodes":[
                   {"databaseId":21,"body":"Null here","path":"A.java",
                    "author":{"login":"scanner[bot]","__typename":"Bot"}},
                   {"databaseId":22,"body":"Fixed","path":"A.java",
                    "author":{"login":"jagt-bot","__typename":"User"}}]}}]}}}}}
                """,
                ""));

    Answer<List<RequestNote>> answer = github.notes(host, REQUEST);

    assertThat(answer.facts().get())
        .extracting(RequestNote::id, RequestNote::kind, RequestNote::resolution)
        .containsExactly(
            tuple("issue-11", AuthorKind.PERSON, Resolution.UNKNOWN),
            tuple("21", AuthorKind.BOT, Resolution.RESOLVED),
            tuple("22", AuthorKind.ACTOR, Resolution.RESOLVED));
  }

  @Test
  void accountOnTheProjectsBotListIsBotThoughTheHostCallsItUser() {
    HostSettings listed =
        hostSettings()
            .withReview(
                new ProjectConfig.Review(List.of(), List.of(), List.of(), List.of("ci-helper")))
            .build();
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"data\":{\"viewer\":{\"login\":\"me\"},\"repository\":{\"pullRequest\":{"
                    + "\"comments\":{\"nodes\":[{\"databaseId\":5,\"body\":\"x\","
                    + "\"author\":{\"login\":\"CI-Helper\",\"__typename\":\"User\"}}]},"
                    + "\"reviewThreads\":{\"nodes\":[]}}}}}",
                ""));

    Answer<List<RequestNote>> answer = github.notes(listed, REQUEST);

    assertThat(answer.facts().get()).extracting(RequestNote::kind).containsExactly(AuthorKind.BOT);
  }

  @Test
  void replyIsPostedToTheThreadsCommentAndItsIdReturned() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "{\"id\":99}", ""));

    Answer<String> answer =
        github.post(host, REQUEST, new NoteDraft("Done", "21", NoteAnchor.NONE));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence("api", "-X", "POST", "repos/acme/shop/pulls/7/comments/21/replies");
    assertThat(answer.facts()).contains("99");
  }

  @Test
  void likesAndDislikesAreCountedPerNote() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"content\":\"+1\"},{\"content\":\"+1\"},{\"content\":\"-1\"},"
                    + "{\"content\":\"heart\"}]",
                ""));

    Answer<Map<String, Reaction>> answer = github.reactions(host, REQUEST, List.of("21"));

    assertThat(answer.facts().get()).containsEntry("21", new Reaction(2, 1));
  }

  @Test
  void failedReactionReadFailsTheWholeAnswerNeverMapMissingNote() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "gh: API rate limit exceeded"));

    Answer<Map<String, Reaction>> answer = github.reactions(host, REQUEST, List.of("21", "22"));

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).contains("rate limit exceeded");
  }

  @Test
  void topLevelNoteIsPostedAsAnIssueCommentAndItsIdTellsSo() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "{\"id\":99}", ""));

    Answer<String> answer = github.post(host, REQUEST, new NoteDraft("Done", "", NoteAnchor.NONE));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue()).contains("repos/acme/shop/issues/7/comments");
    assertThat(answer.facts()).contains("issue-99");
  }

  @Test
  void replyToTopLevelNoteIsAnotherTopLevelNote() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "{\"id\":99}", ""));

    github.post(host, REQUEST, new NoteDraft("Done", "issue-11", NoteAnchor.NONE));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue()).contains("repos/acme/shop/issues/7/comments");
  }

  @Test
  void noteWithFileAndLineIsPostedOnThatLineOfTheCommit() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "{\"id\":99}", ""));

    Answer<String> answer =
        github.post(
            host,
            REQUEST,
            new NoteDraft("Null here", "", new NoteAnchor("src/A.java", 11, "0123abc")));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .contains(
            "repos/acme/shop/pulls/7/comments",
            "commit_id=0123abc",
            "path=src/A.java",
            "line=11",
            "side=RIGHT");
    assertThat(answer.facts()).contains("99");
  }

  @Test
  void topLevelNoteReactionsAreReadFromTheIssueCommentEndpoint() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[{\"content\":\"+1\"}]", ""));

    Answer<Map<String, Reaction>> answer = github.reactions(host, REQUEST, List.of("issue-11"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .contains("repos/acme/shop/issues/comments/11/reactions?per_page=100");
    assertThat(answer.facts().get()).containsEntry("issue-11", new Reaction(1, 0));
  }

  @Test
  void mergedRequestIsReadAsMerged() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"title\":\"t\",\"body\":\"b\",\"baseRefName\":\"main\","
                    + "\"headRefOid\":\"0123abc\",\"state\":\"MERGED\"}",
                ""),
            new Processes.Result(0, "diff", ""));

    Answer<RequestChange> answer = github.change(host, REQUEST);

    assertThat(answer.facts().get().state()).isEqualTo(RequestState.MERGED);
  }
}
