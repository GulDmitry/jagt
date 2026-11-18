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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KindEngineTest {

  private static final String DIST =
      """
      kind: Secret
      stringData:
        JAGT_PASSWORD_HASH: ""
      ---
      spec:
        replicas: 1
              image: registry.example.com/my-jagt:latest
      """;

  @TempDir Path dir;

  private final List<List<String>> seen = new ArrayList<>();
  private final StandBoard board = mock(StandBoard.class);
  private final Process forwarding = mock(Process.class);
  private final List<Integer> forwards = new ArrayList<>();
  private Path root;
  private Path manifest;

  @BeforeEach
  void placeTheRootAndTheDistributedManifest() throws IOException {
    root = Files.createDirectories(dir.resolve("jagt-stand-41-1"));
    manifest = Files.writeString(dir.resolve("kubernetes.yml.dist"), DIST);
    when(forwarding.isAlive()).thenReturn(true);
  }

  @Test
  void upCreatesClusterNamedAfterTheRootLoadsTheImageAndAppliesTheManifestAsDistributed()
      throws IOException {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "");
        };
    when(board.ready()).thenReturn(false, true);

    new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> {
              forwards.add(local);
              return forwarding;
            },
            manifest,
            Duration.ofMinutes(5))
        .up();

    assertThat(seen.get(0))
        .containsSubsequence("kind", "create", "cluster", "--name", "jagt-stand-41-1");
    assertThat(seen)
        .anyMatch(command ->
            command.containsAll(List.of("kind", "load", "docker-image", "jagt-stand:run")));
    assertThat(seen)
        .anyMatch(command -> command.containsAll(List.of("kubectl", "scale", "--replicas=1")));
    assertThat(Files.readString(root.resolve("kubernetes.yml")))
        .contains("image: jagt-stand:run")
        .contains("replicas: 0")
        .contains("ORCHESTRATOR_CONFIG_FILE: /data/stand/jagt.yml");
    assertThat(forwards).containsExactly(9000);
  }

  @Test
  void upCopiesEveryFixtureIntoTheVolumeThroughTheSeedPodBeforeTheJagtPodStarts() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "");
        };
    when(board.ready()).thenReturn(true);

    new KindEngine(
            root, 9000, board, shell, (where, local) -> forwarding, manifest, Duration.ofMinutes(5))
        .up();

    List<String> copies =
        seen.stream()
            .filter(command -> command.contains("cp"))
            .map(command -> command.getLast())
            .toList();
    assertThat(copies)
        .containsExactly(
            "seed:/data/stand/jagt.yml",
            "seed:/data/stand/password-hash",
            "seed:/data/stand/fake-claude.sh",
            "seed:/data/stand/origin.git");
    assertThat(seen.stream().map(command -> command.contains("scale")).toList())
        .containsSubsequence(false, true);
    assertThat(seen).anyMatch(command -> command.contains("pod/seed"));
  }

  @Test
  void clusterIsDeletedAndTheFailureNamesTheStepWhenTheClusterCannotBeCreated() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(command.contains("create") ? 1 : 0, "no docker");
        };

    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    assertThatThrownBy(kind::up)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("cannot create the cluster in jagt-stand-41-1: no docker");
    assertThat(seen.getLast())
        .containsExactly("kind", "delete", "cluster", "--name", "jagt-stand-41-1");
  }

  @Test
  void manifestThatLostAnEditedLineFailsNamingIt() throws IOException {
    Files.writeString(manifest, "kind: Secret\n");
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "");
        };

    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    assertThatThrownBy(kind::up)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("no longer holds registry.example.com/my-jagt:latest");
    assertThat(seen.getLast()).contains("delete");
  }

  @Test
  void closeKeepsThePodLogThenDeletesTheCluster() throws IOException {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "pod log");
        };

    new KindEngine(
            root, 9000, board, shell, (where, local) -> forwarding, manifest, Duration.ofMinutes(5))
        .close();

    assertThat(root.resolve("jagt.out")).hasContent("pod log");
    assertThat(seen.getLast())
        .containsExactly("kind", "delete", "cluster", "--name", "jagt-stand-41-1");
  }

  @Test
  void closeFailsNamingTheClusterAndTheExitWhenTheClusterCannotBeDeleted() {
    StandShell.Runner shell =
        (cwd, command, env) -> new StandShell.Result(command.contains("delete") ? -1 : 0, "");
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    assertThatThrownBy(kind::close)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("cluster jagt-stand-41-1 still exists")
        .hasMessageContaining("exited -1");
  }

  @Test
  void startFailureKeepsItsCauseWhenTheClusterCannotBeDeletedEither() {
    StandShell.Runner shell =
        (cwd, command, env) ->
            new StandShell.Result(
                command.contains("create") || command.contains("delete") ? 1 : 0, "");
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    assertThatThrownBy(kind::up)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("cannot create the cluster in jagt-stand-41-1")
        .hasSuppressedException(
            new StandFailure(
                "cluster jagt-stand-41-1 still exists: kind delete cluster exited 1: "));
  }

  @Test
  void execRunsInsideTheDeploymentsPod() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "4242\n");
        };
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    long pid = kind.pid();

    assertThat(pid).isEqualTo(4242);
    assertThat(seen.get(0))
        .containsExactly("kubectl", "exec", "deployment/jagt", "--", "pgrep", "-f", "jagt.jar");
  }

  @Test
  void windowsAreReadFromTheTmuxServerInsideThePod() {
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(0, "work\nverify\n");
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    assertThat(kind.windows()).isEqualTo("work verify");
  }

  @Test
  void noWindowsWhenThePodHasNoTmuxServer() {
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(1, "no server");
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    assertThat(kind.windows()).isEmpty();
    assertThat(kind.screens()).isEmpty();
  }

  @Test
  void deletingThePodForwardsThePortAgainAndWaitsForTheBoard() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "log");
        };
    when(board.ready()).thenReturn(false, true);
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> {
              forwards.add(local);
              return forwarding;
            },
            manifest,
            Duration.ofMinutes(5));

    JagtEngine restarted = kind.restart(Restart.UNIT);

    assertThat(restarted).isSameAs(kind);
    assertThat(seen)
        .anyMatch(command -> command.containsAll(List.of("kubectl", "delete", "pod", "app=jagt")));
    assertThat(forwards).containsExactly(9000);
  }

  @Test
  void upgradeLoadsTheNewImageIntoTheClusterAndRollsTheDeploymentOntoIt() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "log");
        };
    when(board.ready()).thenReturn(true);
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    kind.restart(Restart.UPGRADE);

    assertThat(seen)
        .anyMatch(command ->
            command.containsAll(List.of("docker", "tag", "jagt-stand:run", "jagt-stand:next")));
    assertThat(seen)
        .anyMatch(command ->
            command.containsAll(List.of("kind", "load", "docker-image", "jagt-stand:next")));
    assertThat(seen)
        .anyMatch(command ->
            command.containsAll(List.of("kubectl", "set", "image", "jagt=jagt-stand:next")));
    assertThat(seen).anyMatch(command -> command.containsAll(List.of("rollout", "status")));
  }

  @Test
  void killingTheJvmWaitsForTheBoardToGoDownThenForItToComeBack() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "log");
        };
    when(board.ready()).thenReturn(true, false, true);
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    JagtEngine restarted = kind.restart(Restart.JVM);

    assertThat(restarted).isSameAs(kind);
    assertThat(seen).anyMatch(command -> command.contains("pkill"));
  }

  @Test
  void jvmThatWasNotRunningFailsTheRestart() {
    StandShell.Runner shell =
        (cwd, command, env) -> new StandShell.Result(command.contains("pkill") ? 1 : 0, "none");
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    assertThatThrownBy(() -> kind.restart(Restart.JVM))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("jagt's JVM was not running in its pod");
  }

  @Test
  void jagtThatKeepsAnsweringAfterTheKillFailsTheRestartAndDeletesTheCluster() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "log");
        };
    when(board.ready()).thenReturn(true);
    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofSeconds(-1));

    assertThatThrownBy(() -> kind.restart(Restart.JVM))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not stop on port 9000");
    assertThat(seen.getLast()).contains("delete");
  }

  @Test
  void jagtThatNeverComesUpFailsNamingThePortAndDeletesTheCluster() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "log");
        };
    when(board.ready()).thenReturn(false);

    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofSeconds(-1));

    assertThatThrownBy(kind::up)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not come up on port 9000");
    assertThat(seen.getLast()).contains("delete");
  }

  @Test
  void deadForwardIsOpenedAgainWhileTheBoardIsDown() {
    StandShell.Runner shell = (cwd, command, env) -> new StandShell.Result(0, "log");
    Process dead = mock(Process.class);
    when(dead.isAlive()).thenReturn(false);
    when(board.ready()).thenReturn(false, false, true);
    List<Process> opened = new ArrayList<>(List.of(dead, forwarding));
    int[] count = {0};

    new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> opened.get(count[0]++),
            manifest,
            Duration.ofMinutes(5))
        .up();

    assertThat(count[0]).isEqualTo(2);
  }

  @Test
  void terminateScalesThePodAwayAndWaitsForItsExit() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, "");
        };

    when(board.ready()).thenReturn(false, true);
    KindEngine kind =
        new KindEngine(
                root,
                9000,
                board,
                shell,
                (where, local) -> forwarding,
                manifest,
                Duration.ofMinutes(5))
            .up();
    seen.clear();

    kind.terminate();

    assertThat(seen.getFirst())
        .containsSubsequence("kubectl", "scale", "deployment/jagt", "--replicas=0");
    assertThat(seen.getLast()).containsSubsequence("kubectl", "wait", "--for=delete", "pod");
    verify(forwarding).destroy();
  }

  @Test
  void stateIsReadFromTheVolumeThroughTheSeedPodThatIsRemovedAfterwards() {
    StandShell.Runner shell =
        (cwd, command, env) -> {
          seen.add(command);
          return new StandShell.Result(0, command.contains("cat") ? "{\"tasks\":{}}" : "");
        };

    String state =
        new KindEngine(
                root,
                9000,
                board,
                shell,
                (where, local) -> forwarding,
                manifest,
                Duration.ofMinutes(5))
            .state();

    assertThat(state).isEqualTo("{\"tasks\":{}}");
    assertThat(seen.getFirst()).containsSubsequence("kubectl", "apply", "-f");
    assertThat(seen.getLast()).containsSubsequence("kubectl", "delete", "pod", "seed");
  }

  @Test
  void stateThatCannotBeReadFailsNamingTheCluster() {
    StandShell.Runner shell =
        (cwd, command, env) -> new StandShell.Result(command.contains("cat") ? 1 : 0, "no file");

    KindEngine kind =
        new KindEngine(
            root,
            9000,
            board,
            shell,
            (where, local) -> forwarding,
            manifest,
            Duration.ofMinutes(5));

    assertThatThrownBy(kind::state)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("cannot read state.json in jagt-stand-41-1: no file");
  }
}
