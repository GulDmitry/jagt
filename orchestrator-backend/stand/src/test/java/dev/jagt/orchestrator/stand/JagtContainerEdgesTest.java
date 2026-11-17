package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JagtContainerEdgesTest {

  @TempDir Path dir;

  @Test
  void theScreensAreTheTmuxPanesTextWhenTheServerRuns() {
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(0, "=== s:0 work\nhi\n");
    StandBoard board = new StandBoard(9000);

    JagtContainer container = new JagtContainer(dir, 9000, board, shell, Map.of());

    assertThat(container.screens()).isEqualTo("=== s:0 work\nhi\n");
    assertThat(container.port()).isEqualTo(9000);
    assertThat(container.board()).isSameAs(board);
  }

  @Test
  void noScreensWhenTheTmuxServerIsNotRunning() {
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(1, "no server");

    JagtContainer container = new JagtContainer(dir, 9000, new StandBoard(9000), shell, Map.of());

    assertThat(container.screens()).isEmpty();
  }

  @Test
  void theJagtPidIsZeroWhenNoJvmRuns() {
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(1, "");

    JagtContainer container = new JagtContainer(dir, 9000, new StandBoard(9000), shell, Map.of());

    assertThat(container.pid()).isZero();
  }

  @Test
  void restartingFailsWhenNoJvmRunsInTheContainerAndKeepsTheEarlierOutput() throws Exception {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          return new StandShell.Result(command.contains("pkill") ? 1 : 0, "log");
        };
    JagtContainer container = new JagtContainer(dir, 9000, new StandBoard(9000), shell, Map.of());

    assertThatThrownBy(container::restart)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("was not running in its container");
    assertThat(dir.resolve("jagt.out.before-restart")).hasContent("log");
    assertThat(Files.exists(dir.resolve("jagt.out"))).isFalse();
  }

  @Test
  void theLogOfContainerWhoseRootIsGoneCannotBeKept() {
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(0, "log");
    JagtContainer container =
        new JagtContainer(dir.resolve("gone"), 9000, new StandBoard(9000), shell, Map.of());

    assertThatThrownBy(container::logs).isInstanceOf(StandFailure.class);
  }
}
