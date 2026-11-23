package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

class StandEngineTest {

  private final StandEngine.Launch launch = (r, p, pw, e) -> mock(JagtEngine.class);
  private final Map<String, StandEngine.Launch> launchers =
      Map.of(StandEngine.HOST, launch, StandEngine.COMPOSE, launch, StandEngine.KIND, launch);

  @Test
  void composeEngineAsksDockerForTheHostPortAndHostEngineProbesOne() {
    StandEngine compose =
        new StandEngine(StandEngine.COMPOSE, tool -> true, name -> null, launchers);
    StandEngine host = new StandEngine(StandEngine.HOST, tool -> true, name -> null, launchers);

    assertThat(compose.hostPort()).isZero();
    assertThat(host.hostPort()).isGreaterThan(1023);
  }

  @Test
  void publicBoardPortIsTheProbedOneElseTheImagesOwn() {
    StandEngine engine =
        new StandEngine(StandEngine.COMPOSE, tool -> true, name -> null, launchers);

    assertThat(engine.publicPort(8123)).isEqualTo(8123);
    assertThat(engine.publicPort(0)).isEqualTo(8290);
  }

  @Test
  void skipsWithOneSentenceWhenDockerIsAbsent(@TempDir Path root) {
    StandEngine engine =
        new StandEngine(StandEngine.COMPOSE, tool -> false, name -> null, launchers);

    assertThatThrownBy(() -> engine.start(root, 1, ""))
        .isInstanceOf(TestAbortedException.class)
        .hasMessage(
            "docker is absent on this machine, so the stand run is skipped;"
                + " -Pengine=host runs it on the host");
  }

  @Test
  void imageRunWithNeitherClaudeCredentialFailsNamingBothVariables() {
    StandEngine engine =
        new StandEngine(StandEngine.COMPOSE, tool -> true, name -> null, launchers);

    assertThatThrownBy(engine::needsCredential)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("CLAUDE_CODE_OAUTH_TOKEN or ANTHROPIC_API_KEY");
  }

  @Test
  void imageRunWithOneClaudeCredentialIsReady() {
    StandEngine engine =
        new StandEngine(
            StandEngine.COMPOSE,
            tool -> true,
            name -> name.equals("ANTHROPIC_API_KEY") ? "set" : null,
            launchers);

    assertThatCode(engine::needsCredential).doesNotThrowAnyException();
  }

  @Test
  void hostRunNeedsNoCredentialVariable() {
    StandEngine engine = new StandEngine(StandEngine.HOST, tool -> false, name -> null, launchers);

    assertThatCode(engine::needsCredential).doesNotThrowAnyException();
  }

  @Test
  void kindSkipsNamingTheToolItLacks(@TempDir Path root) {
    List<String> asked = new ArrayList<>();
    StandEngine engine =
        new StandEngine(
            StandEngine.KIND,
            tool -> {
              asked.add(tool);
              return !tool.equals("kubectl");
            },
            name -> null,
            launchers);

    assertThatThrownBy(() -> engine.start(root, 1, ""))
        .isInstanceOf(TestAbortedException.class)
        .hasMessageStartingWith("kubectl is absent");
    assertThat(asked).containsExactly("docker", "kind", "kubectl");
  }

  @Test
  void hostNeedsNoTool(@TempDir Path root) {
    StandEngine engine = new StandEngine(StandEngine.HOST, tool -> false, name -> null, launchers);

    assertThat(engine.start(root, 1, "")).isNotNull();
  }

  @Test
  void refusesAnEngineNobodyBuiltNamingTheThree(@TempDir Path root) {
    StandEngine engine = new StandEngine("vm", tool -> true, name -> null, launchers);

    assertThatThrownBy(() -> engine.start(root, 1, ""))
        .isInstanceOf(StandFailure.class)
        .hasMessage("unknown stand.engine vm: host, compose or kind");
  }

  @Test
  void scenarioReadingTheRootIsSkippedOnKind() {
    StandEngine engine = new StandEngine(StandEngine.KIND, tool -> true, name -> null, launchers);

    assertThatThrownBy(() -> engine.needsRoot("happy-path"))
        .isInstanceOf(TestAbortedException.class)
        .hasMessageContaining("happy-path reads the run's root");
  }

  @Test
  void scenarioReadingTheRootRunsOnCompose() {
    StandEngine engine =
        new StandEngine(StandEngine.COMPOSE, tool -> true, name -> null, launchers);

    engine.needsRoot("happy-path");

    assertThat(engine.sharesRoot()).isTrue();
  }

  @Test
  void kindSeesItsFixturesUnderTheVolumeAndTheImagePort(@TempDir Path root) {
    StandEngine engine = new StandEngine(StandEngine.KIND, tool -> true, name -> null, launchers);

    assertThat(engine.inside(root)).isEqualTo(Path.of("/data/stand"));
    assertThat(engine.insidePort(40123)).isEqualTo(8290);
  }

  @Test
  void hostSeesItsRootAndItsOwnPort(@TempDir Path root) {
    StandEngine engine = new StandEngine(StandEngine.HOST, tool -> true, name -> null, launchers);

    assertThat(engine.inside(root)).isEqualTo(root);
    assertThat(engine.insidePort(40123)).isEqualTo(40123);
    assertThat(engine.image()).isFalse();
  }

  @Test
  void markerNamesTheContainerUnderComposeAndTheClusterUnderKind(@TempDir Path dir) {
    Path root = dir.resolve("jagt-stand-41-1");
    StandMarker marker = StandMarker.started("server");

    StandMarker compose =
        new StandEngine(StandEngine.COMPOSE, tool -> true, name -> null, launchers)
            .mark(marker, root);
    StandMarker kind =
        new StandEngine(StandEngine.KIND, tool -> true, name -> null, launchers).mark(marker, root);
    StandMarker host =
        new StandEngine(StandEngine.HOST, tool -> true, name -> null, launchers).mark(marker, root);

    assertThat(compose.container()).isEqualTo("jagt-stand-41-1");
    assertThat(compose.cluster()).isEmpty();
    assertThat(kind.cluster()).isEqualTo("jagt-stand-41-1");
    assertThat(kind.container()).isEmpty();
    assertThat(host).isEqualTo(marker);
  }
}
