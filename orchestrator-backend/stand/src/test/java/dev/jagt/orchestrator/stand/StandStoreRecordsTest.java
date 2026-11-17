package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandStoreRecordsTest {

  @TempDir Path directory;

  @Test
  void seededTicketIsReadBackWithItsProjectAndListed() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedTicket("ABC-42", "Title", "Body", "open");

    assertThat(store.ticket("ABC-42").path("project").asString()).isEqualTo("ABC");
    assertThat(store.allTickets()).hasSize(1);
  }

  @Test
  void anUnknownTicketIsFailure() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");

    assertThatThrownBy(() -> store.ticket("ABC-1")).isInstanceOf(StandFailure.class);
  }

  @Test
  void secondRequestOfTheSameSourceReturnsTheOpenOne() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("feature", "main", "t", "d");

    store.createRequest("feature", "main", "other", "other");

    assertThat(store.allRequests()).hasSize(1);
    assertThat(store.request(1).path("title").asString()).isEqualTo("t");
  }

  @Test
  void updatingBothFieldsChangesTitleAndDescription() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("feature", "main", "t", "d");

    store.updateRequest(1, "new title", "new description");

    assertThat(store.request(1).path("title").asString()).isEqualTo("new title");
  }

  @Test
  void anUnknownRequestIsFailure() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");

    assertThatThrownBy(() -> store.request(9)).isInstanceOf(StandFailure.class);
  }

  @Test
  void resolvedThreadIsMarkedResolved() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("feature", "main", "t", "d");
    store.addThread(1, "greet.sh", 3, "stiff");

    store.resolve(1, 1);

    assertThat(store.request(1).path("threads").get(0).path("resolved").asBoolean()).isTrue();
  }

  @Test
  void replyToAnUnknownThreadIsFailure() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("feature", "main", "t", "d");

    assertThatThrownBy(() -> store.reply(1, 5, "agent", "ok")).isInstanceOf(StandFailure.class);
  }

  @Test
  void theReviewerIsNotAwaitedWhenEveryThreadIsResolved() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("feature", "main", "t", "d");
    store.addThread(1, "greet.sh", 3, "stiff");
    store.resolve(1, 1);

    assertThat(store.awaitsTheReviewer()).isFalse();
  }
}
