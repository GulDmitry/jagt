package dev.jagt.orchestrator.port.host;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LayoutTest {

  @Test
  void keepsJagtsOwnStateAndMarksInItsFolderAndRecordsInTheirs() {
    Layout layout = new Layout(Path.of("/d"), null);

    assertThat(layout.stateFile()).isEqualTo(Path.of("/d/jagt/state.json"));
    assertThat(layout.token()).isEqualTo(Path.of("/d/jagt/master-token"));
    assertThat(layout.checkerVerdicts("qa")).isEqualTo(Path.of("/d/jagt/qa-verdicts"));
    assertThat(layout.stops()).isEqualTo(Path.of("/d/records/stops.jsonl"));
    assertThat(layout.finished()).isEqualTo(Path.of("/d/records/finished.jsonl"));
    assertThat(layout.archive()).isEqualTo(Path.of("/d/archive"));
    assertThat(layout.memory()).isEqualTo(Path.of("/d/memory"));
    assertThat(layout.logs()).isEqualTo(Path.of("/d/logs"));
  }

  @Test
  void cutsOneCheckoutOfRoleBranchPerProjectUnderTheDataFolder() {
    Layout layout = new Layout(Path.of("/d"), null);

    assertThat(layout.roleBranchWorktree("web", "jagt/acceptance-qa"))
        .isEqualTo(Path.of("/d/role-branches/web-jagt-acceptance-qa"));
  }

  @Test
  void followsConfiguredStateFileWithJagtsMarksBesideIt() {
    Layout layout = new Layout(Path.of("/d"), Path.of("/s/own.json"));

    assertThat(layout.actStopped()).isEqualTo(Path.of("/s/act-stopped"));
    assertThat(layout.stops()).isEqualTo(Path.of("/d/records/stops.jsonl"));
  }

  @Test
  void keepsEveryFileOfTaskInItsOwnFolderWhateverTheStateFile() {
    Layout layout = new Layout(Path.of("/d"), Path.of("/s/own.json"));

    assertThat(layout.worktree("ABC-1", "web")).isEqualTo(Path.of("/d/tasks/ABC-1/worktrees/web"));
    assertThat(layout.deployWorktree("ABC-1", "web"))
        .isEqualTo(Path.of("/d/tasks/ABC-1/worktrees/web-deploy"));
    assertThat(layout.revertWorktree("ABC-1", "web"))
        .isEqualTo(Path.of("/d/tasks/ABC-1/worktrees/web-revert"));
    assertThat(layout.planBaseline("ABC-1")).isEqualTo(Path.of("/d/tasks/ABC-1/plan-baseline.md"));
    assertThat(layout.ticketText("ABC-1")).isEqualTo(Path.of("/d/tasks/ABC-1/ticket-text.md"));
    assertThat(layout.scratch("ABC-1")).isEqualTo(Path.of("/d/tasks/ABC-1/scratch"));
  }

  @Test
  void clonesProjectsUnderTheDataFolder() {
    Layout layout = new Layout(Path.of("/d"), Path.of("/s/own.json"));

    assertThat(layout.clone("web")).isEqualTo(Path.of("/d/projects/web"));
  }

  @Test
  void createsEveryFolderOfTheTreeAtStart() {
    Layout layout = new Layout(Path.of("/d"), null);

    assertThat(layout.folders())
        .containsExactly(
            Path.of("/d/jagt"),
            Path.of("/d/projects"),
            Path.of("/d/tasks"),
            Path.of("/d/archive"),
            Path.of("/d/records"),
            Path.of("/d/memory"),
            Path.of("/d/logs"));
  }

  @Test
  void knowsTheScratchOfTheTaskInItsFolderOrWhereAnOlderVersionCutIt() {
    Layout layout = new Layout(Path.of("/d"), null);

    assertThat(layout.isScratchOf(Path.of("/d/tasks/ABC-1/scratch"), "ABC-1")).isTrue();
    assertThat(layout.isScratchOf(Path.of("/older/scratch/ABC-1"), "ABC-1")).isTrue();
    assertThat(layout.isScratchOf(Path.of("/older/scratch/ABC-2"), "ABC-1")).isFalse();
    assertThat(layout.isScratchOf(Path.of("/elsewhere/ABC-1"), "ABC-1")).isFalse();
    assertThat(layout.isScratchOf(Path.of("/d/tasks/ABC-1/worktrees/web"), "ABC-1")).isFalse();
  }

  @Test
  void stopsGitAtTheTasksFolderOrAtTheScratchFolderAnOlderVersionUsed() {
    Layout layout = new Layout(Path.of("/d"), null);

    assertThat(layout.scratchCeiling(Path.of("/d/tasks/ABC-1/scratch")))
        .isEqualTo(Path.of("/d/tasks"));
    assertThat(layout.scratchCeiling(Path.of("/older/scratch/ABC-1")))
        .isEqualTo(Path.of("/older/scratch"));
    assertThat(layout.scratchCeiling(Path.of("/d/tasks/ABC-1/worktrees/web"))).isNull();
  }

  @Test
  void placesEachFileOfAnOlderFlatFolderIntoItsHomeAndLeavesTheTokenBehind() {
    Layout layout = new Layout(Path.of("/d"), null);

    assertThat(layout.placed())
        .containsEntry("state.json", Path.of("/d/jagt/state.json"))
        .containsEntry("state.json.bak", Path.of("/d/jagt/state.json.bak"))
        .containsEntry("interactions.jsonl.1", Path.of("/d/records/interactions.jsonl.1"))
        .containsEntry("stops-reviewed.jsonl", Path.of("/d/records/stops-reviewed.jsonl"))
        .doesNotContainKey("master-token");
    assertThat(layout.placedVerdicts("qa-verdicts")).isEqualTo(Path.of("/d/jagt/qa-verdicts"));
    assertThat(layout.placedVerdicts("notes.txt")).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "plan-baselines,ABC-1.md,/d/tasks/ABC-1/plan-baseline.md",
    "ticket-texts,ABC-1.md,/d/tasks/ABC-1/ticket-text.md",
    "repos,web,/d/projects/web",
    "logs,jagt-backend.log,/d/logs/jagt-backend.log",
    "memory,routing.md,/d/memory/routing.md",
    "archive,2026-ABC-1,/d/archive/2026-ABC-1"
  })
  void placesEachEntryOfAnOlderFolderIntoTheTaskOrFolderItBelongsTo(
      String folder, String entry, String expected) {
    Layout layout = new Layout(Path.of("/d"), null);

    assertThat(layout.placedEntries().getOrDefault(folder, name -> Path.of("/none")).apply(entry))
        .isEqualTo(Path.of(expected));
  }
}
