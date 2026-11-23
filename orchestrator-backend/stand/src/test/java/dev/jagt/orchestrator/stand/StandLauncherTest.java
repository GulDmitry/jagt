package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

class StandLauncherTest {

  @TempDir Path dir;

  private final StandSlots slots = new StandSlots(2);
  private final List<String> passwords = new ArrayList<>();
  private final JagtEngine jagt = mock(JagtEngine.class);
  private Function<Path, Optional<Process>> asleep;
  private final Duration poll = Duration.ofMillis(1);
  private StandEngine.Launch hostLaunch;
  private StandEngine.Launch containerLaunch;
  private Map<String, StandEngine.Launch> launches;
  private StandTeardown teardown;
  private StandLauncher.Place place;

  @BeforeEach
  void createJagtAndPlaces() {
    when(jagt.pid()).thenReturn(99L);
    when(jagt.port()).thenReturn(8123);
    when(jagt.screens()).thenReturn("");
    hostLaunch = (root, port, password, env) -> jagt;
    containerLaunch =
        (root, port, password, env) -> {
          passwords.add(password);
          return jagt;
        };
    asleep = root -> Optional.empty();
    launches = Map.of(StandEngine.HOST, hostLaunch, StandEngine.COMPOSE, containerLaunch);
    teardown =
        new StandTeardown(
            new StandArchive(
                dir.resolve("runs"),
                dir.resolve("recordings"),
                dir.resolve("failed"),
                (run, result) -> {},
                base ->
                    new StandTraces(base, base.resolve("claude.json"), base.resolve("projects"))),
            slots,
            System.out,
            Duration.ofSeconds(1));
    place =
        new StandLauncher.Place(
            dir.resolve("base"), Path.of(System.getProperty("stand.repo", "../..")));
  }

  @Test
  void liveRunStartsUnderFreshRootWithItsMarkerNamingJagt() throws Exception {
    StandEngine engine = new StandEngine(StandEngine.HOST, tool -> false, name -> null, launches);
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    try (StandRun run = launcher.start("happy", StandMode.JUDGE, null, StandConfig.Posts.NONE)) {
      assertThat(run.root().getParent()).isEqualTo(dir.resolve("base").toRealPath());
      assertThat(StandRoots.named(run.root())).isTrue();
      assertThat(StandMarker.read(run.root(), 0).jagtPid()).isEqualTo(99);
      assertThat(StandMarker.read(run.root(), 0).scenario()).isEqualTo("happy/judge");
      assertThat(run.root().resolve("jagt.yml")).exists();
      assertThat(run.root().resolve("recording.jsonl")).doesNotExist();
    }
  }

  @Test
  void secondRunUnderTheSameBaseTakesAnotherRoot() throws Exception {
    StandEngine engine = new StandEngine(StandEngine.HOST, tool -> false, name -> null, launches);
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    try (StandRun first = launcher.start("happy", StandMode.JUDGE, null, StandConfig.Posts.NONE);
        StandRun second = launcher.start("happy", StandMode.JUDGE, null, StandConfig.Posts.NONE)) {
      assertThat(first.root()).isNotEqualTo(second.root());
    }
  }

  @Test
  void rootNameIsNotTakenAgainOnceItsFolderIsGone() throws Exception {
    StandEngine engine = new StandEngine(StandEngine.HOST, tool -> false, name -> null, launches);
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);
    Path gone;
    try (StandRun first = launcher.start("happy", StandMode.JUDGE, null, StandConfig.Posts.NONE)) {
      gone = first.root();
    }

    try (StandRun second = launcher.start("happy", StandMode.JUDGE, null, StandConfig.Posts.NONE)) {
      assertThat(second.root().getFileName()).isNotEqualTo(gone.getFileName());
    }
  }

  @Test
  void replayedRunPlacesItsRecordingWithTheHostsAppUrl() throws Exception {
    Path recording = Files.writeString(dir.resolve("kept.jsonl"), "{\"url\":\"{app}\"}");
    StandEngine engine = new StandEngine(StandEngine.HOST, tool -> false, name -> null, launches);
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    try (StandRun run =
        launcher.start("happy", StandMode.JUDGE, recording, StandConfig.Posts.NONE)) {
      assertThat(Files.readString(run.root().resolve("recording.jsonl")))
          .startsWith("{\"url\":\"http://127.0.0.1:")
          .endsWith("/app\"}");
    }
  }

  @Test
  void containerRunGetsPasswordHashAndItsMarkerNamesTheContainer() throws Exception {
    StandEngine engine =
        new StandEngine(StandEngine.COMPOSE, tool -> true, name -> "set", launches);
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    try (StandRun run = launcher.start("happy", StandMode.JUDGE, null, StandConfig.Posts.NONE)) {
      assertThat(run.root().resolve("password-hash")).content().startsWith("$2");
      assertThat(passwords).singleElement().asString().isNotEmpty();
      assertThat(StandMarker.read(run.root(), 0).container())
          .isEqualTo(run.root().getFileName().toString());
    }
  }

  @Test
  void jagtThatFailsToStartKeepsTheRootMarkedFailedAndFreesTheSlot() {
    StandEngine engine =
        new StandEngine(
            StandEngine.HOST,
            tool -> false,
            name -> null,
            Map.of(
                StandEngine.HOST,
                (root, port, password, env) -> {
                  throw new StandFailure("jagt did not come up");
                }));
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    assertThatThrownBy(() -> launcher.start("happy", StandMode.JUDGE, null, StandConfig.Posts.NONE))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("stand run happy did not start; kept ")
        .hasMessageContaining(", evidence in ")
        .hasMessageContaining("failed/happy-judge-jagt-stand-")
        .hasCauseInstanceOf(StandFailure.class);
    assertThat(dir.resolve("base").toFile().listFiles())
        .singleElement()
        .satisfies(root -> assertThat(StandMarker.read(root.toPath(), 0).failed()).isTrue());
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          slots.take();
          slots.take();
        });
  }

  @Test
  void containerEngineWithoutDockerSkipsTheRunAndFreesTheSlot() {
    StandEngine engine =
        new StandEngine(StandEngine.COMPOSE, tool -> false, name -> null, launches);
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    assertThatThrownBy(() -> launcher.start("happy", StandMode.JUDGE, null, StandConfig.Posts.NONE))
        .isInstanceOf(TestAbortedException.class);
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          slots.take();
          slots.take();
        });
  }

  @Test
  void serverRunWritesItsFixturesStartsTheEngineAndMarksTheRun() throws Exception {
    StandEngine engine =
        new StandEngine(
            StandEngine.COMPOSE,
            tool -> true,
            name -> null,
            Map.of(StandEngine.COMPOSE, containerLaunch));
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    try (ServerRun run = launcher.server("server")) {
      assertThat(run.root().resolve("origin.git")).isDirectory();
      assertThat(run.root().resolve("jagt.yml")).exists();
      assertThat(passwords).singleElement().asString().isNotEmpty();
      assertThat(StandMarker.read(run.root(), 0).container())
          .isEqualTo(run.root().getFileName().toString());
      assertThat(StandMarker.read(run.root(), 0).jagtPid()).isEqualTo(99);
    }
  }

  @Test
  void serverRunHasItsOwnTmuxFolderSoTmuxNeverFallsBackToTheMachinesSocket() throws Exception {
    StandEngine engine =
        new StandEngine(
            StandEngine.COMPOSE,
            tool -> true,
            name -> null,
            Map.of(StandEngine.COMPOSE, containerLaunch));
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    try (ServerRun run = launcher.server("server")) {
      assertThat(run.root().resolve("tmux")).isDirectory();
    }
  }

  @Test
  void serverRunThatFailsToStartKeepsTheRootMarkedFailedAndFreesTheSlot() {
    StandEngine engine =
        new StandEngine(
            StandEngine.HOST,
            tool -> false,
            name -> null,
            Map.of(
                StandEngine.HOST,
                (root, port, password, env) -> {
                  throw new StandFailure("jagt did not come up");
                }));
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    assertThatThrownBy(() -> launcher.server("server"))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("server run server did not start; kept ")
        .hasMessageContaining(", evidence in ")
        .hasMessageContaining("failed/server-jagt-stand-");
    assertThat(dir.resolve("base").toFile().listFiles())
        .singleElement()
        .satisfies(root -> assertThat(StandMarker.read(root.toPath(), 0).failed()).isTrue());
    assertTimeoutPreemptively(Duration.ofSeconds(5), slots::take);
    assertTimeoutPreemptively(Duration.ofSeconds(5), slots::take);
  }

  @Test
  void runReadingTheRootIsSkippedOnKindAndFreesTheSlot() {
    StandEngine engine =
        new StandEngine(
            StandEngine.KIND,
            tool -> true,
            name -> null,
            Map.of(StandEngine.KIND, containerLaunch));
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    assertThatThrownBy(() -> launcher.start("happy", StandMode.JUDGE, null, StandConfig.Posts.NONE))
        .isInstanceOf(TestAbortedException.class)
        .hasMessageContaining("happy reads the run's root");
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          slots.take();
          slots.take();
        });
  }

  @Test
  void serverRunWithoutDockerSkipsAndLeavesNoRoot() {
    StandEngine engine =
        new StandEngine(StandEngine.COMPOSE, tool -> false, name -> null, launches);
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);

    assertThatThrownBy(() -> launcher.server("server")).isInstanceOf(TestAbortedException.class);
    assertThat(dir.resolve("base")).doesNotExist();
  }

  @Test
  void serverRunWaitsForSlotWhileTheLiveRunsHoldThemAll() throws Exception {
    StandEngine engine = new StandEngine(StandEngine.COMPOSE, tool -> true, name -> null, launches);
    StandLauncher launcher = new StandLauncher(engine, teardown, place, asleep, poll);
    slots.take();
    slots.take();

    CompletableFuture<ServerRun> waiting =
        CompletableFuture.supplyAsync(() -> {
          try {
            return launcher.server("server");
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });

    await().during(Duration.ofMillis(300)).atMost(WaitCeiling.LIMIT).until(() -> !waiting.isDone());
    slots.free();
    try (ServerRun run = waiting.get(30, TimeUnit.SECONDS)) {
      assertThat(run.root().resolve("jagt.yml")).exists();
    }
  }
}
