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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

  @Test
  void restartKeepsTheFirstOutputThenLaunchesAgainOnTheSamePort(@TempDir Path root)
      throws Exception {
    Files.writeString(root.resolve("jagt.out"), "first out");
    when(board.ready()).thenReturn(true);
    Process[] launches = {first, second};
    int[] count = {0};
    JagtProcess jagt =
        new JagtProcess(
            root, 8123, board, (dir, port) -> launches[count[0]++], Duration.ofMinutes(5));
    jagt.begin();

    JagtEngine restarted = jagt.restart();

    assertThat(restarted.pid()).isEqualTo(2);
    assertThat(restarted.port()).isEqualTo(8123);
    assertThat(root.resolve("jagt.out.before-restart")).hasContent("first out");
    verify(firstHandle).destroy();
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
}
