package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JagtContainerLifecycleTest {

  @TempDir Path dir;

  @Test
  void containerIsUpWhenComposeStartsItAndTheBoardAnswers() {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "");
        };
    StandBoard board = mock(StandBoard.class);
    when(board.ready()).thenReturn(true);

    JagtContainer container =
        new JagtContainer(dir, 9000, board, shell, Map.of(), Duration.ofMinutes(5)).up();

    assertThat(container.port()).isEqualTo(9000);
    assertThat(seen.get(0)).containsSubsequence("up", "-d", "--wait", "--pull", "never");
  }

  @Test
  void containerThatComposeCannotStartIsTakenDownAndTheStartFailsWithComposesOutput() {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(command.contains("up") ? 1 : 0, "no such image");
        };
    JagtContainer container =
        new JagtContainer(
            dir, 9000, mock(StandBoard.class), shell, Map.of(), Duration.ofMinutes(5));

    assertThatThrownBy(container::up)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not start")
        .hasMessageContaining("no such image");
    assertThat(seen.getLast()).contains("down");
  }

  @Test
  void jagtThatNeverAnswersWithinItsStartupFailsTheStart() {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "");
        };
    JagtContainer container =
        new JagtContainer(
            dir, 9000, mock(StandBoard.class), shell, Map.of(), Duration.ofSeconds(-1));

    assertThatThrownBy(container::up)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not come up on port 9000");
    assertThat(seen.getLast()).contains("down");
  }

  @Test
  void restartKillsTheJvmWaitsForTheBoardToGoDownThenForItToComeBack() {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "log");
        };
    StandBoard board = mock(StandBoard.class);
    when(board.ready()).thenReturn(false, true);
    JagtContainer container =
        new JagtContainer(dir, 9000, board, shell, Map.of(), Duration.ofMinutes(5));

    JagtEngine restarted = container.restart();

    assertThat(restarted).isSameAs(container);
    assertThat(seen).anyMatch(command -> command.contains("pkill"));
  }

  @Test
  void jagtThatKeepsAnsweringAfterTheKillFailsTheRestartAndTakesTheContainerDown() {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "log");
        };
    StandBoard board = mock(StandBoard.class);
    when(board.ready()).thenReturn(true);
    JagtContainer container =
        new JagtContainer(dir, 9000, board, shell, Map.of(), Duration.ofSeconds(-1));

    assertThatThrownBy(container::restart)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not stop on port 9000");
    assertThat(seen.getLast()).contains("down");
  }
}
