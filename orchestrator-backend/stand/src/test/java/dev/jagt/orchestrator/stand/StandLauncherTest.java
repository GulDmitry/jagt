package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

class StandLauncherTest {

  @TempDir Path dir;

  private final StandSlots slots = new StandSlots(2);
  private final List<String> passwords = new ArrayList<>();
  private final JagtEngine jagt = mock(JagtEngine.class);
  private StandEngine.Launch hostLaunch;
  private StandEngine.Launch containerLaunch;
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
    StandEngine engine =
        new StandEngine(StandEngine.LOCAL, () -> false, name -> null, hostLaunch, containerLaunch);
    StandLauncher launcher =
        new StandLauncher(engine, teardown, place, root -> Optional.empty(), Duration.ofMillis(1));

    try (StandRun run = launcher.start("happy", StandMode.JUDGE, null)) {
      assertThat(run.root().getParent()).isEqualTo(dir.resolve("base").toRealPath());
      assertThat(run.root().getFileName().toString()).startsWith(StandRoots.PREFIX).endsWith("-1");
      assertThat(StandMarker.read(run.root(), 0).jagtPid()).isEqualTo(99);
      assertThat(StandMarker.read(run.root(), 0).scenario()).isEqualTo("happy/judge");
      assertThat(run.root().resolve("jagt.yml")).exists();
      assertThat(run.root().resolve("recording.jsonl")).doesNotExist();
    }
  }

  @Test
  void secondRunUnderTheSameBaseTakesTheNextRootNumber() throws Exception {
    StandEngine engine =
        new StandEngine(StandEngine.LOCAL, () -> false, name -> null, hostLaunch, containerLaunch);
    StandLauncher launcher =
        new StandLauncher(engine, teardown, place, root -> Optional.empty(), Duration.ofMillis(1));

    try (StandRun first = launcher.start("happy", StandMode.JUDGE, null);
        StandRun second = launcher.start("happy", StandMode.JUDGE, null)) {
      assertThat(first.root()).isNotEqualTo(second.root());
      assertThat(second.root().getFileName().toString()).endsWith("-2");
    }
  }

  @Test
  void replayedRunPlacesItsRecordingWithTheHostsAppUrl() throws Exception {
    Path recording = Files.writeString(dir.resolve("kept.jsonl"), "{\"url\":\"{app}\"}");
    StandEngine engine =
        new StandEngine(StandEngine.LOCAL, () -> false, name -> null, hostLaunch, containerLaunch);
    StandLauncher launcher =
        new StandLauncher(engine, teardown, place, root -> Optional.empty(), Duration.ofMillis(1));

    try (StandRun run = launcher.start("happy", StandMode.JUDGE, recording)) {
      assertThat(Files.readString(run.root().resolve("recording.jsonl")))
          .startsWith("{\"url\":\"http://127.0.0.1:")
          .endsWith("/app\"}");
    }
  }

  @Test
  void containerRunGetsPasswordHashAndItsMarkerNamesTheContainer() throws Exception {
    StandEngine engine =
        new StandEngine(
            StandEngine.CONTAINER, () -> true, name -> null, hostLaunch, containerLaunch);
    StandLauncher launcher =
        new StandLauncher(engine, teardown, place, root -> Optional.empty(), Duration.ofMillis(1));

    try (StandRun run = launcher.start("happy", StandMode.JUDGE, null)) {
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
            StandEngine.LOCAL,
            () -> false,
            name -> null,
            (root, port, password, env) -> {
              throw new StandFailure("jagt did not come up");
            },
            containerLaunch);
    StandLauncher launcher =
        new StandLauncher(engine, teardown, place, root -> Optional.empty(), Duration.ofMillis(1));

    assertThatThrownBy(() -> launcher.start("happy", StandMode.JUDGE, null))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("stand run happy did not start; kept ")
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
        new StandEngine(
            StandEngine.CONTAINER, () -> false, name -> null, hostLaunch, containerLaunch);
    StandLauncher launcher =
        new StandLauncher(engine, teardown, place, root -> Optional.empty(), Duration.ofMillis(1));

    assertThatThrownBy(() -> launcher.start("happy", StandMode.JUDGE, null))
        .isInstanceOf(TestAbortedException.class);
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          slots.take();
          slots.take();
        });
  }
}
