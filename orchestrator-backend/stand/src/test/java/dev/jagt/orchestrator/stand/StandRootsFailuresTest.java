package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandRootsFailuresTest {

  @TempDir Path directory;

  @Test
  void nameThatIsNoStandRootHasNoPid() {
    assertThatThrownBy(() -> StandRoots.pidOf("other-41-1")).isInstanceOf(StandFailure.class);
  }

  @Test
  void theStandRootNameCarriesItsPid() {
    assertThat(StandRoots.pidOf("jagt-stand-41-3")).isEqualTo(41);
  }

  @Test
  void baseThatIsGoneCannotBePlanned() {
    assertThatThrownBy(() -> StandRoots.plan(directory.resolve("gone"), pid -> false))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("cannot list");
  }

  @Test
  void trustInAnUnreadableConfigIsFailure() throws Exception {
    Path config = Files.writeString(directory.resolve(".claude.json"), "{not json");
    StandTraces traces = new StandTraces(directory, config, directory.resolve("projects"));

    assertThatThrownBy(() -> traces.remove(root -> true)).isInstanceOf(StandFailure.class);
  }

  @Test
  void noTranscriptsAreDeletedWhenTheProjectsDirectoryIsAbsent() {
    StandTraces traces =
        new StandTraces(
            directory, directory.resolve(".claude.json"), directory.resolve("no-projects"));

    assertThat(traces.remove(root -> true)).isEqualTo(new StandTraces.Removed(0, 0));
  }

  @Test
  void baseThatIsGoneHasNoTraces() {
    StandTraces traces =
        new StandTraces(
            directory.resolve("gone"),
            directory.resolve(".claude.json"),
            directory.resolve("projects"));

    assertThat(traces.remove(root -> true)).isEqualTo(new StandTraces.Removed(0, 0));
  }
}
