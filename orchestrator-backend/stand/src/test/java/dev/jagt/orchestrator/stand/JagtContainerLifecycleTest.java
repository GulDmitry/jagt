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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    assertThat(seen.get(1)).containsSubsequence("up", "-d", "--wait", "--pull", "never");
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

  @ParameterizedTest
  @CsvSource({"JVM,pkill", "UNIT,restart"})
  void restartActsOnTheJvmOrTheServiceThenWaitsForTheBoardToComeBack(Restart how, String acts) {
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

    JagtEngine restarted = container.restart(how);

    assertThat(restarted).isSameAs(container);
    assertThat(seen).anyMatch(command -> command.contains(acts));
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

    assertThatThrownBy(() -> container.restart(Restart.JVM))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not stop on port 9000");
    assertThat(seen.getLast()).contains("down");
  }

  @Test
  void upgradeTagsTheImageAndRecreatesTheContainerFromTheNewTag() {
    List<List<String>> seen = new ArrayList<>();
    List<Map<String, String>> environments = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          environments.add(env);
          return new StandShell.Result(0, "log");
        };
    StandBoard board = mock(StandBoard.class);
    when(board.ready()).thenReturn(true);
    JagtContainer container =
        new JagtContainer(dir, 9000, board, shell, Map.of(), Duration.ofMinutes(5));

    container.restart(Restart.UPGRADE);

    assertThat(seen)
        .anyMatch(
            command -> command.equals(List.of("docker", "tag", "jagt-stand", "jagt-stand-next")));
    assertThat(seen.getLast()).contains("up", "-d", "--wait");
    assertThat(environments.getLast()).containsEntry("JAGT_IMAGE", "jagt-stand-next");
  }

  @Test
  void containerThatCannotBeReplacedFailsTheRestartAndIsTakenDown() {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(command.contains("restart") ? 1 : 0, "no such service");
        };
    JagtContainer container =
        new JagtContainer(
            dir, 9000, mock(StandBoard.class), shell, Map.of(), Duration.ofMinutes(5));

    assertThatThrownBy(() -> container.restart(Restart.UNIT))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not restart: no such service");
    assertThat(seen.getLast()).contains("down");
  }

  @Test
  void theDataFolderIsTheRootAndTheLogIsComposes() {
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(0, "line one");
    JagtContainer container =
        new JagtContainer(
            dir, 9000, mock(StandBoard.class), shell, Map.of(), Duration.ofMinutes(5));

    assertThat(container.data()).isEqualTo(dir);
    assertThat(container.log()).isEqualTo("line one");
  }

  @Test
  void imageThatCannotBeTaggedFailsTheUpgradeBeforeAnyContainerIsReplaced() {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(command.contains("tag") ? 1 : 0, "no such image");
        };
    JagtContainer container =
        new JagtContainer(
            dir, 9000, mock(StandBoard.class), shell, Map.of(), Duration.ofMinutes(5));

    assertThatThrownBy(() -> container.restart(Restart.UPGRADE))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not tag the new image: no such image");
    assertThat(seen).noneMatch(command -> command.contains("up"));
  }

  @Test
  void imageThatCannotStartFailsTheUpgradeAndTakesTheContainerDown() {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(command.contains("up") ? 1 : 0, "pull denied");
        };
    JagtContainer container =
        new JagtContainer(
            dir, 9000, mock(StandBoard.class), shell, Map.of(), Duration.ofMinutes(5));

    assertThatThrownBy(() -> container.restart(Restart.UPGRADE))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not start the new image: pull denied");
    assertThat(seen.getLast()).contains("down");
  }

  @Test
  void terminateSendsSigtermToTheServiceThenWaitsUntilNoContainerRuns() {
    List<List<String>> seen = new ArrayList<>();
    int[] polls = {0};
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, command.contains("ps") && polls[0]++ < 1 ? "abc123" : "");
        };
    JagtContainer container =
        new JagtContainer(
            dir, 9000, mock(StandBoard.class), shell, Map.of(), Duration.ofMinutes(5));

    container.terminate();

    assertThat(seen.getFirst()).containsSubsequence("kill", "-s", "TERM", "jagt");
    assertThat(polls[0]).isEqualTo(2);
  }

  @Test
  void signalThatCannotBeSentFailsTheTermination() {
    StandShell.Runner shell =
        (cwd, command, env) -> new StandShell.Result(command.contains("kill") ? 1 : 0, "no such");
    JagtContainer container =
        new JagtContainer(
            dir, 9000, mock(StandBoard.class), shell, Map.of(), Duration.ofMinutes(5));

    assertThatThrownBy(container::terminate)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not take SIGTERM: no such");
  }

  @Test
  void containerThatKeepsRunningAfterSigtermFailsTheTerminationAndIsTakenDown() {
    List<List<String>> seen = new ArrayList<>();
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "abc123");
        };
    JagtContainer container =
        new JagtContainer(
            dir, 9000, mock(StandBoard.class), shell, Map.of(), Duration.ofSeconds(-1));

    assertThatThrownBy(container::terminate)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not exit after SIGTERM");
    assertThat(seen.getLast()).contains("down");
  }
}
