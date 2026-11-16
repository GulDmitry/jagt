package dev.jagt.orchestrator.port.host;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LayoutTest {

  @Test
  void keepsStateAndItsRecordsBesideEachOtherInTheDataFolder() {
    Layout layout = new Layout(Path.of("/d"), null);

    assertThat(layout.stateFile()).isEqualTo(Path.of("/d/state.json"));
    assertThat(layout.masterToken()).isEqualTo(Path.of("/d/master-token"));
    assertThat(layout.stops()).isEqualTo(Path.of("/d/stops.jsonl"));
    assertThat(layout.finished()).isEqualTo(Path.of("/d/finished.jsonl"));
    assertThat(layout.archive()).isEqualTo(Path.of("/d/archive"));
    assertThat(layout.checkerVerdicts("qa")).isEqualTo(Path.of("/d/qa-verdicts"));
  }

  @Test
  void followsConfiguredStateFileWithEveryFileBesideIt() {
    Layout layout = new Layout(Path.of("/d"), Path.of("/s/own.json"));

    assertThat(layout.actStopped()).isEqualTo(Path.of("/s/act-stopped"));
    assertThat(layout.scratch()).isEqualTo(Path.of("/s/scratch"));
  }

  @Test
  void cutsWorktreesAndClonesUnderTheDataFolderWhateverTheStateFile() {
    Layout layout = new Layout(Path.of("/d"), Path.of("/s/own.json"));

    assertThat(layout.worktrees()).isEqualTo(Path.of("/d/worktrees"));
    assertThat(layout.clone("web")).isEqualTo(Path.of("/d/repos/web"));
  }
}
