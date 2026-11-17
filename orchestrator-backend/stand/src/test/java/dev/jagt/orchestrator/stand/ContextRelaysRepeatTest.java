package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.role.SessionBrief;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ContextRelaysRepeatTest {

  @TempDir Path worktree;

  @Test
  void anUnchangedContextIsRelayedOnce() throws Exception {
    Files.writeString(worktree.resolve("task_context.md"), "same");
    ContextRelays relays = new ContextRelays(mock(SessionBrief.class));
    var state =
        JsonMapper.builder()
            .build()
            .readTree(
                "{\"repos\":[{\"worktreePath\":\""
                    + worktree.toString().replace("\\", "\\\\")
                    + "\"}]}");

    var first = relays.changed("ABC-42", state);
    var second = relays.changed("ABC-42", state);

    assertThat(first).isPresent();
    assertThat(second).isEmpty();
  }

  @Test
  void taskWithNoWorktreeRelaysNothing() {
    ContextRelays relays = new ContextRelays(mock(SessionBrief.class));

    assertThat(relays.changed("ABC-42", JsonMapper.builder().build().readTree("{}"))).isEmpty();
  }
}
