package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandStoreFailuresTest {

  @TempDir Path directory;

  @Test
  void storeCannotBeMadeUnderFile() throws Exception {
    Path file = Files.writeString(directory.resolve("file"), "x");

    assertThatThrownBy(() -> new StandStore(file, "http://127.0.0.1:1"))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("cannot create the stand directory");
  }

  @Test
  void storeWhoseDirectoryWasRemovedCannotListItsTickets() {
    StandStore store = new StandStore(directory.resolve("store"), "http://127.0.0.1:1");
    StandShell.deleteTree(directory.resolve("store/tickets"));

    assertThatThrownBy(store::allTickets)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("cannot list");
  }

  @Test
  void ticketCannotBeSeededWhenItsDirectoryWasRemoved() {
    StandStore store = new StandStore(directory.resolve("store"), "http://127.0.0.1:1");
    StandShell.deleteTree(directory.resolve("store/tickets"));

    assertThatThrownBy(() -> store.seedTicket("ABC-42", "t", "d", "open"))
        .isInstanceOf(RuntimeException.class);
  }
}
