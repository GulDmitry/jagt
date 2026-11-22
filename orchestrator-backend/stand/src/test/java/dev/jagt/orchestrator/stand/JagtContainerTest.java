package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JagtContainerTest {

  @Test
  void closeKeepsTheLogThenTakesTheStackDownWithItsVolumesAndOrphans(@TempDir Path dir)
      throws Exception {
    Path root = Files.createDirectories(dir.resolve("jagt-stand-41-1"));
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "container log");
        };

    new JagtContainer(root, 9000, new StandBoard(9000), shell, Map.of(), Duration.ofMinutes(5))
        .close();

    assertThat(seen.get(0)).containsSubsequence("-p", "jagt-stand-41-1", "logs", "--no-color");
    assertThat(seen.get(1)).contains("cp");
    assertThat(seen.get(2))
        .containsSubsequence("-p", "jagt-stand-41-1", "down", "-v", "--remove-orphans");
    assertThat(root.resolve("jagt.out")).hasContent("container log");
  }

  @Test
  void windowsAreReadFromTheTmuxServerInsideTheContainer(@TempDir Path dir) {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "work\nverify\n");
        };

    String windows =
        new JagtContainer(dir, 9000, new StandBoard(9000), shell, Map.of(), Duration.ofMinutes(5))
            .windows();

    assertThat(windows).isEqualTo("work verify");
    assertThat(seen.get(0))
        .containsSubsequence("exec", "-T", "-e", "TMUX_TMPDIR=/tmp/jagt-tmux", "jagt", "tmux");
  }

  @Test
  void noWindowsWhenTheTmuxServerIsNotRunning(@TempDir Path dir) {
    StandShell.Runner shell =
        (cwd, command, env) -> new StandShell.Result(1, "no server running on /tmp/jagt-tmux");

    String windows =
        new JagtContainer(dir, 9000, new StandBoard(9000), shell, Map.of(), Duration.ofMinutes(5))
            .windows();

    assertThat(windows).isEmpty();
  }

  @Test
  void theJagtPidIsTheJvmsInsideTheContainer(@TempDir Path dir) {
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(0, "4242\n");

    long pid =
        new JagtContainer(dir, 9000, new StandBoard(9000), shell, Map.of(), Duration.ofMinutes(5))
            .pid();

    assertThat(pid).isEqualTo(4242);
  }

  @Test
  void everyComposeCallCarriesTheRunsEnvironment(@TempDir Path dir) {
    List<Map<String, String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(env);
          return new StandShell.Result(0, "");
        };

    new JagtContainer(
            dir,
            9000,
            new StandBoard(9000),
            shell,
            Map.of("JAGT_PORT", "9000"),
            Duration.ofMinutes(5))
        .windows();

    assertThat(seen.getFirst())
        .containsEntry("JAGT_PORT", "9000")
        .containsEntry("JAGT_IMAGE", "jagt-stand");
  }

  @Test
  void containerRunsAsTheRootsOwnerSoTheRunnerCanDeleteWhatItWrote(@TempDir Path dir)
      throws IOException {
    List<Map<String, String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(env);
          return new StandShell.Result(0, "");
        };

    new JagtContainer(dir, 9000, new StandBoard(9000), shell, Map.of(), Duration.ofMinutes(5))
        .windows();

    assertThat(seen.getFirst())
        .containsEntry("JAGT_USER", Files.getAttribute(dir, "unix:uid") + ":0");
  }

  @Test
  void theDataFolderIsPreparedInItsVolumeBeforeTheServiceStarts(@TempDir Path dir)
      throws Exception {
    Path root = Files.createDirectories(dir.resolve("jagt-stand-41-1"));
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "");
        };

    StandBoard board = mock(StandBoard.class);
    when(board.ready()).thenReturn(true);

    new JagtContainer(root, 9000, board, shell, Map.of(), Duration.ofMinutes(5)).up();

    assertThat(seen.get(0)).contains("run", "--rm", "--no-deps", "sh");
    assertThat(seen.get(0).getLast()).contains("mkdir -p " + root.resolve("jagt"));
    assertThat(seen.get(1)).contains("up", "-d", "--wait");
  }

  @Test
  void stateIsCopiedOutOfTheContainerNotReadFromTheHostFolder(@TempDir Path dir) throws Exception {
    Path root = Files.createDirectories(dir.resolve("jagt-stand-41-1"));
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          try {
            Files.writeString(Path.of(command.getLast()), "{\"tasks\":{}}");
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
          return new StandShell.Result(0, "");
        };

    String state =
        new JagtContainer(root, 9000, new StandBoard(9000), shell, Map.of(), Duration.ofMinutes(5))
            .state();

    assertThat(state).isEqualTo("{\"tasks\":{}}");
    assertThat(seen.get(0)).containsSubsequence("cp", "jagt:" + root.resolve("jagt/state.json"));
  }

  @Test
  void stateNamesTheCauseWhenTheCopyFails(@TempDir Path dir) throws Exception {
    Path root = Files.createDirectories(dir.resolve("jagt-stand-41-1"));
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(1, "no such container");

    assertThatThrownBy(() ->
            new JagtContainer(
                    root, 9000, new StandBoard(9000), shell, Map.of(), Duration.ofMinutes(5))
                .state())
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("no such container");
  }

  @Test
  void theComposeFileMountsTheDataVolumeBeneathTheBindMountedRoot() throws Exception {
    String compose = Files.readString(Path.of("src", "stand", "compose", "compose.stand.yml"));

    assertThat(compose)
        .contains("- jagt-data:${JAGT_STAND_ROOT}/jagt")
        .contains("\nvolumes:\n  jagt-data:\n");
  }
}
