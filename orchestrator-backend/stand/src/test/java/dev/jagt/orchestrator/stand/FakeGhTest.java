package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FakeGhTest {

  @TempDir Path directory;

  @Test
  void listsTheRequestsOfOthersAwaitingReviewWithTheirAuthorAndHead() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Add app.sh", "", "outside-dev", "abc123", "");
    FakeGh gh = new FakeGh(store);

    String printed =
        gh.run(
            List.of(
                "pr", "list", "--search", "review-requested:@me", "-R", "stand.example/o/proj"));

    assertThat(JsonMapper.builder().build().readTree(printed).path(0).path("url").asString())
        .isEqualTo("https://stand.example/o/proj/pull/101");
    assertThat(printed).contains("outside-dev").contains("abc123");
  }

  @Test
  void readsChangeWithItsStateInUpperCase() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Add app.sh", "Please look", "outside-dev", "abc123", "diff text");
    FakeGh gh = new FakeGh(store);

    String printed =
        gh.run(List.of("pr", "view", "https://stand.example/o/proj/pull/101", "--json", "title"));

    assertThat(printed)
        .contains("\"state\":\"OPEN\"")
        .contains("\"headRefOid\":\"abc123\"")
        .contains("\"body\":\"Please look\"");
  }

  @Test
  void printsTheDiffOfRequest() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Add app.sh", "", "outside-dev", "abc123", "diff text");
    FakeGh gh = new FakeGh(store);

    String printed = gh.run(List.of("pr", "diff", "https://stand.example/o/proj/pull/101"));

    assertThat(printed).isEqualTo("diff text");
  }

  @Test
  void postedNoteIsTheActorsAndTheHookRuns() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Add app.sh", "", "outside-dev", "abc123", "");
    FakeGh gh = new FakeGh(store);
    AtomicInteger hooked = new AtomicInteger();
    gh.afterPost(hooked::incrementAndGet);

    String printed =
        gh.run(
            List.of(
                "api",
                "--hostname",
                "stand.example",
                "-X",
                "POST",
                "repos/o/proj/issues/101/comments",
                "-f",
                "body=text"));

    assertThat(printed).isEqualTo("{\"id\":1}");
    assertThat(store.note(1).path("kind").asString()).isEqualTo("actor");
    assertThat(store.note(1).path("body").asString()).isEqualTo("text");
    assertThat(hooked.get()).isEqualTo(1);
  }

  @Test
  void graphqlNamesTheActorAndTypesBotAuthorAsBot() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Add app.sh", "", "outside-dev", "abc123", "");
    store.addNote(101, "lint-bot", "bot", "unused import", "", 0);
    FakeGh gh = new FakeGh(store);

    String printed = gh.run(List.of("api", "graphql", "-F", "number=101"));

    assertThat(printed)
        .contains("\"login\":\"" + StandStore.ACTOR + "\"")
        .contains("\"__typename\":\"Bot\"");
  }

  @Test
  void inlineNotesAreGroupedIntoThreadWithTheirAnswer() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Add app.sh", "", "outside-dev", "abc123", "");
    store.addNote(101, "lint-bot", "bot", "unused import", "app.sh", 0);
    store.addNote(101, StandStore.ACTOR, "actor", "removed", "", 1);
    FakeGh gh = new FakeGh(store);

    String printed = gh.run(List.of("api", "graphql", "-F", "number=101"));

    assertThat(
            JsonMapper.builder()
                .build()
                .readTree(printed)
                .path("data")
                .path("repository")
                .path("pullRequest")
                .path("reviewThreads")
                .path("nodes")
                .path(0)
                .path("comments")
                .path("nodes")
                .size())
        .isEqualTo(2);
  }

  @Test
  void reactionsAreTalliedAsPlusAndMinusOne() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Add app.sh", "", "outside-dev", "abc123", "");
    store.addNote(101, StandStore.ACTOR, "actor", "text", "", 0);
    store.react(1, 2, 1);
    FakeGh gh = new FakeGh(store);

    String printed =
        gh.run(
            List.of(
                "api", "--hostname", "h", "repos/o/proj/pulls/comments/1/reactions?per_page=100"));

    assertThat(printed)
        .isEqualTo("[{\"content\":\"+1\"},{\"content\":\"+1\"},{\"content\":\"-1\"}]");
  }

  @Test
  void approvalIsRefusedAndRecorded() throws Exception {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    FakeGh gh = new FakeGh(store);

    assertThatThrownBy(() -> gh.run(List.of("pr", "review", "https://h/o/p/pull/1", "--approve")))
        .isInstanceOf(StandFailure.class);

    assertThat(Files.readString(directory.resolve("approvals.log"))).contains("pr review");
  }

  @Test
  void commandItDoesNotKnowIsRefused() {
    FakeGh gh = new FakeGh(new StandStore(directory, "http://127.0.0.1:1"));

    assertThatThrownBy(() -> gh.run(List.of("gist", "create"))).isInstanceOf(StandFailure.class);
  }
}
