package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class JagtProcessLaunchTest {

  private final Process first = mock(Process.class);
  private final ProcessHandle firstHandle = mock(ProcessHandle.class);
  private final Process second = mock(Process.class);
  private final ProcessHandle secondHandle = mock(ProcessHandle.class);
  private final Process dead = mock(Process.class);
  private final ProcessHandle deadHandle = mock(ProcessHandle.class);
  private final StandBoard board = mock(StandBoard.class);

  @BeforeEach
  void stubProcessesAndHandles() {
    when(first.pid()).thenReturn(1L);
    when(first.isAlive()).thenReturn(true);
    when(first.toHandle()).thenReturn(firstHandle);
    when(second.pid()).thenReturn(2L);
    when(second.isAlive()).thenReturn(true);
    when(second.toHandle()).thenReturn(secondHandle);
    when(dead.isAlive()).thenReturn(false);
    when(dead.toHandle()).thenReturn(deadHandle);
    when(firstHandle.descendants()).thenAnswer(call -> Stream.empty());
    when(firstHandle.onExit()).thenReturn(CompletableFuture.completedFuture(firstHandle));
    when(secondHandle.descendants()).thenAnswer(call -> Stream.empty());
    when(secondHandle.onExit()).thenReturn(CompletableFuture.completedFuture(secondHandle));
    when(deadHandle.descendants()).thenAnswer(call -> Stream.empty());
    when(deadHandle.onExit()).thenReturn(CompletableFuture.completedFuture(deadHandle));
  }

  @Test
  void jagtIsUpWhenItsBoardAnswersAndItsPidIsTheLaunchedProcess(@TempDir Path root) {
    when(board.ready()).thenReturn(true);

    JagtProcess jagt =
        new JagtProcess(root, 8123, board, (dir, port) -> first, Duration.ofMinutes(5)).begin();

    assertThat(jagt.pid()).isEqualTo(1);
    assertThat(jagt.port()).isEqualTo(8123);
    assertThat(jagt.board()).isSameAs(board);
  }

  @Test
  void launchFailureNamesTheRootItWasLaunchedUnder(@TempDir Path root) {
    JagtProcess jagt =
        new JagtProcess(
            root,
            8123,
            board,
            (dir, port) -> {
              throw new IOException("no java");
            },
            Duration.ofMinutes(5));

    assertThatThrownBy(jagt::begin)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("cannot launch")
        .hasMessageContaining(root.toString());
  }

  @Test
  void jagtThatDiesBeforeItsBoardAnswersFailsTheStartAndNamesItsOutput(@TempDir Path root) {
    JagtProcess jagt =
        new JagtProcess(root, 8123, board, (dir, port) -> dead, Duration.ofMinutes(5));

    assertThatThrownBy(jagt::begin)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not come up on port 8123")
        .hasMessageContaining("jagt.out");
    verify(deadHandle).destroy();
  }

  @Test
  void jagtThatNeverAnswersWithinItsStartupFailsTheStart(@TempDir Path root) {
    JagtProcess jagt =
        new JagtProcess(root, 8123, board, (dir, port) -> first, Duration.ofSeconds(-1));

    assertThatThrownBy(jagt::begin)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not come up on port 8123");
    verify(firstHandle).destroy();
  }

  @ParameterizedTest
  @EnumSource(
      value = Restart.class,
      names = {"JVM", "UNIT"})
  void restartKeepsTheFirstOutputThenLaunchesAgainOnTheSamePort(Restart how, @TempDir Path root)
      throws Exception {
    Files.writeString(root.resolve("jagt.out"), "first out");
    when(board.ready()).thenReturn(true);
    Process[] launches = {first, second};
    int[] count = {0};
    JagtProcess jagt =
        new JagtProcess(
            root, 8123, board, (dir, port) -> launches[count[0]++], Duration.ofMinutes(5));
    jagt.begin();

    JagtEngine restarted = jagt.restart(how);

    assertThat(restarted.pid()).isEqualTo(2);
    assertThat(restarted.port()).isEqualTo(8123);
    assertThat(root.resolve("jagt.out.before-restart")).hasContent("first out");
    verify(firstHandle).destroy();
  }

  @Test
  void theRootsBinDirectoryComesFirstOnThePathWhenItExists(@TempDir Path root) throws Exception {
    Files.createDirectory(root.resolve("bin"));

    ProcessBuilder command = JagtProcess.command(root, 8123);

    assertThat(command.environment().get("PATH")).startsWith(root.resolve("bin") + ":");
  }

  @Test
  void restartRelaunchesOnTheSamePortWhenAnotherRunTookItInBetween(@TempDir Path root) {
    when(board.ready()).thenReturn(true, false, true);
    Process[] launches = {first, dead, second};
    int[] count = {0};
    JagtProcess jagt =
        new JagtProcess(
            root,
            8123,
            board,
            (dir, port) -> {
              Files.writeString(dir.resolve("jagt.out"), "Address already in use");
              return launches[count[0]++];
            },
            Duration.ofMinutes(5));
    jagt.begin();

    JagtEngine restarted = jagt.restart(Restart.JVM);

    assertThat(restarted.pid()).isEqualTo(2);
    assertThat(restarted.port()).isEqualTo(8123);
  }

  @Test
  void jagtThatDiesOfAnotherCauseIsNotRelaunched(@TempDir Path root) {
    int[] launches = {0};
    JagtProcess jagt =
        new JagtProcess(
            root,
            8123,
            board,
            (dir, port) -> {
              launches[0]++;
              Files.writeString(dir.resolve("jagt.out"), "Out of memory");
              return dead;
            },
            Duration.ofMinutes(5));

    assertThatThrownBy(jagt::begin)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not come up on port 8123");
    assertThat(launches[0]).isEqualTo(1);
  }

  @Test
  void jagtThatKeepsFindingThePortTakenIsRelaunchedThreeTimesThenFailsTheStart(@TempDir Path root) {
    int[] launches = {0};
    JagtProcess jagt =
        new JagtProcess(
            root,
            8123,
            board,
            (dir, port) -> {
              launches[0]++;
              Files.writeString(dir.resolve("jagt.out"), "Address already in use");
              return dead;
            },
            Duration.ofMinutes(5));

    assertThatThrownBy(jagt::begin)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not come up on port 8123");
    assertThat(launches[0]).isEqualTo(4);
  }

  @Test
  void theCommandRunsTheStagedJarOnThePortUnderTheRootWithoutTheHumansTmux(@TempDir Path root) {
    ProcessBuilder command = JagtProcess.command(root, 8123);

    assertThat(command.command()).contains("-jar", "--server.port=8123");
    assertThat(command.directory()).isEqualTo(root.toFile());
    assertThat(command.environment())
        .containsEntry("ORCHESTRATOR_ROOT", root.toString())
        .containsEntry("ORCHESTRATOR_DATA", root.toString())
        .containsEntry("LOG_FILE", root.resolve("jagt-backend.log").toString())
        .containsEntry("TMUX_TMPDIR", root.resolve("tmux").toString())
        .doesNotContainKeys("TMUX", "TMUX_PANE", "ORCHESTRATOR_CONFIG_FILE");
    assertThat(command.environment().keySet()).noneMatch(name -> name.startsWith("CLAUDE"));
  }

  @Test
  void closeEndsTheOwnerOfItsLaunches(@TempDir Path root) throws IOException {
    when(board.ready()).thenReturn(true);
    JagtProcess.Launcher launcher = mock(JagtProcess.Launcher.class);
    when(launcher.launch(root, 8123)).thenReturn(first);
    JagtProcess jagt = new JagtProcess(root, 8123, board, launcher, Duration.ofMinutes(5)).begin();

    jagt.close();

    verify(launcher).close();
  }

  @Test
  void restartKeepsTheOwnerOfItsLaunchesOpenForTheNextLaunch(@TempDir Path root)
      throws IOException {
    when(board.ready()).thenReturn(true);
    JagtProcess.Launcher launcher = mock(JagtProcess.Launcher.class);
    when(launcher.launch(root, 8123)).thenReturn(first, second);
    JagtProcess jagt = new JagtProcess(root, 8123, board, launcher, Duration.ofMinutes(5)).begin();

    jagt.restart(Restart.JVM);

    verify(launcher, org.mockito.Mockito.never()).close();
  }

  @Test
  void upgradeIsSkippedBecauseJarHasNoImage(@TempDir Path root) {
    when(board.ready()).thenReturn(true);
    JagtProcess jagt =
        new JagtProcess(root, 8123, board, (dir, port) -> first, Duration.ofMinutes(5)).begin();

    assertThatThrownBy(() -> jagt.restart(Restart.UPGRADE))
        .isInstanceOf(org.opentest4j.TestAbortedException.class)
        .hasMessageContaining("no image to upgrade");
    verify(firstHandle, org.mockito.Mockito.never()).destroy();
  }

  @Test
  void theDataFolderIsTheRootAndTheLogIsJagtsOutput(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("jagt.out"), "started");
    JagtProcess jagt =
        new JagtProcess(root, 8123, board, (dir, port) -> first, Duration.ofMinutes(5));

    assertThat(jagt.data()).isEqualTo(root);
    assertThat(jagt.log()).isEqualTo("started");
  }

  @Test
  void logThatCannotBeReadFailsNamingTheRoot(@TempDir Path root) {
    JagtProcess jagt =
        new JagtProcess(root, 8123, board, (dir, port) -> first, Duration.ofMinutes(5));

    assertThatThrownBy(jagt::log)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("cannot read jagt's output under " + root);
  }

  @Test
  void execRunsTheProgramInTheRoot(@TempDir Path root) throws Exception {
    JagtProcess jagt =
        new JagtProcess(root, 8123, board, (dir, port) -> first, Duration.ofMinutes(5));

    StandShell.Result result = jagt.exec("sh", "-c", "pwd -P");

    assertThat(result.output().strip()).isEqualTo(root.toRealPath().toString());
  }

  @ParameterizedTest
  @CsvSource({"JVM,true", "UNIT,false"})
  void tmuxServerSurvivesTheJvmRestartButNotTheUnitReplacement(Restart how, boolean survives)
      throws Exception {
    Path base = Files.createTempDirectory(Path.of("/tmp"), "j");
    Path root = base.resolve("jagt-stand-1-1");
    Files.createDirectories(root.resolve("tmux/x"));
    Path socket = root.resolve("tmux/x/default");
    when(board.ready()).thenReturn(true);
    Process[] launches = {first, second};
    int[] count = {0};
    JagtProcess jagt =
        new JagtProcess(
            root, 8123, board, (dir, port) -> launches[count[0]++], Duration.ofMinutes(5));
    jagt.begin();
    try {
      StandShell.run(
          root,
          List.of(
              "tmux", "-S", socket.toString(), "new-session", "-d", "-n", "work", "sleep", "30"));

      jagt.restart(how);

      assertThat(StandShell.windows(root).contains("work")).isEqualTo(survives);
    } finally {
      StandShell.run(base, List.of("tmux", "-S", socket.toString(), "kill-server"));
      StandShell.deleteRoot(root);
      Files.deleteIfExists(base);
    }
  }

  @Test
  void terminateStopsJagtsProcessTree() {
    when(board.ready()).thenReturn(true);
    JagtProcess jagt =
        new JagtProcess(Path.of("."), 8123, board, (dir, port) -> first, Duration.ofMinutes(5))
            .begin();

    jagt.terminate();

    verify(firstHandle).destroy();
  }
}
