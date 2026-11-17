package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(seen.get(1))
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

    assertThat(seen).containsExactly(Map.of("JAGT_PORT", "9000"));
  }
}
