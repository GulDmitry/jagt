package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandShellRunTest {

  @TempDir Path directory;

  @Test
  void commandAnswersItsExitAndItsOutput() {
    StandShell.Result result = StandShell.run(directory, List.of("sh", "-c", "echo hi; exit 3"));

    assertThat(result.exit()).isEqualTo(3);
    assertThat(result.output()).isEqualTo("hi\n");
  }

  @Test
  void commandSeesTheEnvironmentItWasGiven() {
    StandShell.Result result =
        StandShell.run(directory, List.of("sh", "-c", "echo $STAND_X"), Map.of("STAND_X", "7"));

    assertThat(result.output()).isEqualTo("7\n");
  }

  @Test
  void commandThatCannotStartAnswersMinusOne() {
    StandShell.Result result = StandShell.run(directory, List.of("no-such-command-here"));

    assertThat(result.exit()).isEqualTo(-1);
  }

  @Test
  void failingGitCommandIsFailureNamingTheCommand() {
    assertThatThrownBy(() -> StandShell.git(directory, "no-such-subcommand"))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("git no-such-subcommand failed");
  }

  @Test
  void gitSucceedsOnlyWhenTheCommandExitsZero() {
    StandShell.git(directory, "init", "--initial-branch=main", ".");

    assertThat(StandShell.gitSucceeds(directory, "status")).isTrue();
    assertThat(StandShell.gitSucceeds(directory, "rev-parse", "--verify", "refs/heads/none"))
        .isFalse();
  }

  @Test
  void directoryThatIsNoStandRootIsNotDeleted() throws Exception {
    Path other = Files.createDirectory(directory.resolve("precious"));

    assertThatThrownBy(() -> StandShell.deleteRoot(other)).isInstanceOf(StandFailure.class);
    assertThat(other).exists();
  }

  @Test
  void treeIsDeletedWithEverythingUnderIt() throws Exception {
    Path tree = Files.createDirectories(directory.resolve("tree/a/b"));
    Files.writeString(tree.resolve("f"), "x");

    StandShell.deleteTree(directory.resolve("tree"));

    assertThat(directory.resolve("tree")).doesNotExist();
  }

  @Test
  void treeThatIsGoneCannotBeDeleted() {
    assertThatThrownBy(() -> StandShell.deleteTree(directory.resolve("gone")))
        .isInstanceOf(StandFailure.class);
  }

  @Test
  void stoppingProcessEndsItAndItsChildren() throws Exception {
    Process parent = new ProcessBuilder("sh", "-c", "sleep 60 & wait").start();

    StandShell.stopTree(parent.toHandle());

    assertThat(parent.isAlive()).isFalse();
  }

  @Test
  void rootWithoutTmuxSocketsHasNoWindowsAndNoScreens() {
    assertThat(StandShell.windows(directory)).isEmpty();
    assertThat(StandShell.screens(directory)).isEmpty();
  }

  @Test
  void rootWithNoTmuxDirectoryKillsNothing() {
    StandShell.killTmux(directory);

    assertThat(directory).exists();
  }

  @Test
  void tmuxSessionShowsItsWindowAndItsScreen() throws Exception {
    Path root = Files.createTempDirectory(Path.of("/tmp"), "j").resolve("jagt-stand-1-1");
    Files.createDirectories(root.resolve("tmux/x"));
    Path socket = root.resolve("tmux/x/default");
    StandShell.run(
        root,
        List.of(
            "tmux",
            "-S",
            socket.toString(),
            "new-session",
            "-d",
            "-n",
            "alpha",
            "echo marker-7; sleep 30"));
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(StandShell.screens(root)).contains("marker-7"));

    final String windows = StandShell.windows(root);
    final String screens = StandShell.screens(root);
    StandShell.killTmux(root);
    StandShell.deleteRoot(root);

    assertThat(windows).isEqualTo("alpha");
    assertThat(screens).contains("=== ").contains("alpha").contains("marker-7");
  }

  @Test
  void theWakeLockIsHeldByProcessWeCanStopOnThisHost() {
    Optional<Process> lock = StandShell.holdAwake(directory);

    lock.ifPresent(Process::destroy);

    assertThat(lock).isNotNull();
  }
}
