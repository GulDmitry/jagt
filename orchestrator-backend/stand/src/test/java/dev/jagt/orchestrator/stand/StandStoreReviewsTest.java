package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class StandStoreReviewsTest {

  @TempDir Path directory;

  @Test
  void requestOfAnotherAuthorAwaitsReviewAndTheActorsOwnDoesNot() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Theirs", "", "outside-dev", "abc", "");
    store.seedPull(1, "Ours", "", StandStore.ACTOR, "def", "");

    List<JsonNode> awaiting = store.awaitingPulls();

    assertThat(awaiting).extracting(pull -> pull.path("number").asInt()).containsExactly(101);
  }

  @Test
  void mergedRequestNoLongerAwaitsReview() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Theirs", "", "outside-dev", "abc", "");

    store.setPullState(101, "merged");

    assertThat(store.awaitingPulls()).isEmpty();
  }

  @Test
  void answerJoinsTheThreadOfTheNoteItAnswers() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Theirs", "", "outside-dev", "abc", "");
    store.addNote(101, "lint-bot", "bot", "unused import", "app.sh", 0);

    JsonNode answer = store.addNote(101, StandStore.ACTOR, "actor", "removed", "", 1);

    assertThat(answer.path("thread").asString()).isEqualTo("T1");
  }

  @Test
  void noteOnTheRequestItselfHasNoThread() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Theirs", "", "outside-dev", "abc", "");

    JsonNode note = store.addNote(101, "someone", "person", "hello", "", 0);

    assertThat(note.path("thread").asString()).isEmpty();
  }

  @Test
  void notesOfOneRequestComeInTheOrderTheyWereWrittenPastNine() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Theirs", "", "outside-dev", "abc", "");
    store.seedPull(102, "Other", "", "outside-dev", "abc", "");
    for (int i = 0; i < 10; i++) {
      store.addNote(101, "someone", "person", "n" + i, "", 0);
    }
    store.addNote(102, "someone", "person", "elsewhere", "", 0);

    List<JsonNode> notes = store.notes(101);

    assertThat(notes)
        .extracting(note -> note.path("id").asInt())
        .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  }

  @Test
  void reactionsAreKeptOnTheirNote() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedPull(101, "Theirs", "", "outside-dev", "abc", "");
    store.addNote(101, StandStore.ACTOR, "actor", "text", "", 0);

    store.react(1, 2, 1);

    assertThat(store.note(1).path("likes").asInt()).isEqualTo(2);
    assertThat(store.note(1).path("dislikes").asInt()).isEqualTo(1);
  }

  @Test
  void noteOnMissingRequestIsFailure() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> store.addNote(7, "someone", "person", "hello", "", 0))
        .isInstanceOf(StandFailure.class);
  }

  @Test
  void approvalAttemptIsKeptWithItsTime() throws Exception {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");

    store.recordApproval("pr review --approve");

    assertThat(Files.readString(directory.resolve("approvals.log")))
        .endsWith(" pr review --approve\n");
  }
}
