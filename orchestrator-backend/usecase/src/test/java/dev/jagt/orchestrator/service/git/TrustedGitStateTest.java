package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class TrustedGitStateTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {});

  private final GitWorktrees git = mock(GitWorktrees.class);

  private final TaskState trustingNothing =
      TaskState.builder("proj", "/wt", TaskStatus.APPROVED).gitTrust(GitTrust.of(Map.of())).build();

  @TempDir Path root;

  private StateService state;
  private TrustedGitState trusted;

  @BeforeEach
  void readsTrustOverTheTempState() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                FakeHost.machine()),
            reducer,
            event -> {});
    trusted = new TrustedGitState(state, git);
  }

  @Test
  void trustsWhatItFindsForTaskWithNothingRecorded() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build());
    when(git.gitState(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("core.editor in /r/.git/config", "a1"));

    Optional<String> distrust = trusted.distrust("ABC-42");

    assertThat(distrust).isEmpty();
    assertThat(state.task("ABC-42").orElseThrow().gate().gitTrust())
        .isEqualTo(GitTrust.of(Map.of("core.editor in /r/.git/config", "a1")));
  }

  @Test
  void parksTheTaskNamingTheKeyAndFileThatChanged() {
    state.putTask("ABC-42", trustingNothing);
    when(git.gitState(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("core.fsmonitor in /r/.git/config", "a1"));

    Optional<String> distrust = trusted.distrust("ABC-42");

    assertThat(distrust)
        .hasValueSatisfying(
            why -> assertThat(why).contains("core.fsmonitor in /r/.git/config added"));
    assertThat(state.task("ABC-42").orElseThrow().gate().gitStateChanged()).isTrue();
  }

  @Test
  void namesThreeChangesAndCountsTheRestWhereMoreChanged() {
    state.putTask("ABC-42", trustingNothing);
    when(git.gitState(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("a in /c", "1", "b in /c", "1", "c in /c", "1", "d in /c", "1"));

    Optional<String> distrust = trusted.distrust("ABC-42");

    assertThat(distrust)
        .contains(
            "git config or hooks changed since trusted: a in /c added; b in /c added;"
                + " c in /c added; 1 more — read them in the Diff tab, trust adopts them");
  }

  @Test
  void namesAllThreeChangesWithoutCountWhereExactlyThreeChanged() {
    state.putTask("ABC-42", trustingNothing);
    when(git.gitState(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("a in /c", "1", "b in /c", "1", "c in /c", "1"));

    Optional<String> distrust = trusted.distrust("ABC-42");

    assertThat(distrust)
        .contains(
            "git config or hooks changed since trusted: a in /c added; b in /c added;"
                + " c in /c added — read them in the Diff tab, trust adopts them");
  }

  @Test
  void showsWhatAddedAndRemovedEntriesHoldAsReadableDiff() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .gitTrust(GitTrust.of(Map.of("/r/.git/hooks/pre-push", "b2")))
            .build());
    when(git.gitState(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("core.fsmonitor in /r/.git/config", "a1"));
    when(git.gitContents(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("core.fsmonitor in /r/.git/config", "/tmp/x"));

    Optional<TaskDiff.RepoDiff> shown = trusted.changes("ABC-42");

    assertThat(shown)
        .hasValueSatisfying(diff ->
            assertThat(diff.diff())
                .isEqualTo(
                    """
                    @@ /r/.git/hooks/pre-push removed
                    @@ core.fsmonitor in /r/.git/config added
                    +/tmp/x
                    """));
  }

  @Test
  void showsNothingWhereGitObeysWhatWasTrusted() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .gitTrust(GitTrust.of(Map.of("a in /c", "1")))
            .build());
    when(git.gitState(List.of(Path.of("/wt")))).thenReturn(Map.of("a in /c", "1"));

    assertThat(trusted.changes("ABC-42")).isEmpty();
  }

  @Test
  void cutsWhatItShowsPastTheCapAndFlagsIt() {
    state.putTask("ABC-42", trustingNothing);
    when(git.gitState(List.of(Path.of("/wt")))).thenReturn(Map.of("a in /c", "1"));
    when(git.gitContents(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("a in /c", "x".repeat(30_000)));

    Optional<TaskDiff.RepoDiff> shown = trusted.changes("ABC-42");

    assertThat(shown).hasValueSatisfying(diff -> assertThat(diff.cut()).isTrue());
    assertThat(shown.orElseThrow().diff()).hasSize(20_000);
  }

  @Test
  void keepsWhatItShowsWholeWhereItIsExactlyTheCap() {
    state.putTask("ABC-42", trustingNothing);
    when(git.gitState(List.of(Path.of("/wt")))).thenReturn(Map.of("a in /c", "1"));
    when(git.gitContents(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("a in /c", "x".repeat(19_981)));

    Optional<TaskDiff.RepoDiff> shown = trusted.changes("ABC-42");

    assertThat(shown).hasValueSatisfying(diff -> assertThat(diff.cut()).isFalse());
  }

  @Test
  void refusesToShowChangesOfTaskThatDoesNotExist() {

    assertThatThrownBy(() -> trusted.changes("ABC-42")).isInstanceOf(Refusal.class);
  }

  @Test
  void readsNoWorktreeForRepoThatHasNone() {
    state.putTask("ABC-42", TaskState.builder("proj", "", TaskStatus.APPROVED).build());
    when(git.gitState(List.of())).thenReturn(Map.of("core.editor in /r/.git/config", "a1"));

    trusted.trust("ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().gate().gitTrust())
        .isEqualTo(GitTrust.of(Map.of("core.editor in /r/.git/config", "a1")));
  }

  @Test
  void refusesToTrustTaskThatDoesNotExist() {

    assertThatThrownBy(() -> trusted.trust("ABC-42")).isInstanceOf(Refusal.class);
  }

  @Test
  void distrustsNothingForTaskThatDoesNotExist() {

    assertThat(trusted.distrust("ABC-42")).isEmpty();
  }

  @Test
  void countsChangeFoundAsTheTasksGitStateRefusal() {
    state.putTask("ABC-42", trustingNothing);
    when(git.gitState(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("core.fsmonitor in /r/.git/config", "a1"));

    trusted.distrust("ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().gate().refusals())
        .containsEntry(RefusalKind.GIT_STATE, 1);
  }

  @Test
  void answersThatTheSessionWaitedWhereTheTaskWasParkedAndItsSessionAsked() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.QUESTION)
            .gitTrust(GitTrust.of(Map.of()).withChanged("core.fsmonitor in /r/.git/config added"))
            .build());

    boolean waited = trusted.trust("ABC-42");

    assertThat(waited).isTrue();
  }

  @Test
  void answersThatNoSessionWaitedWhereTheParkedTasksSessionDidNotAsk() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .gitTrust(GitTrust.of(Map.of()).withChanged("core.fsmonitor in /r/.git/config added"))
            .build());

    boolean waited = trusted.trust("ABC-42");

    assertThat(waited).isFalse();
  }

  @Test
  void keepsParkedTaskParkedWithoutReadingGitAgain() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .gitTrust(GitTrust.of(Map.of()).withChanged("core.fsmonitor in /r/.git/config added"))
            .build());

    Optional<String> distrust = trusted.distrust("ABC-42");

    assertThat(distrust).contains("core.fsmonitor in /r/.git/config added");
    verifyNoInteractions(git);
  }

  @Test
  void refusesWithoutParkingWhereGitCannotBeRead() {
    state.putTask("ABC-42", trustingNothing);
    when(git.gitState(any())).thenThrow(new IllegalStateException("git rev-parse in /wt failed"));

    Optional<String> distrust = trusted.distrust("ABC-42");

    assertThat(distrust)
        .contains("git state of ABC-42 could not be read: git rev-parse in /wt failed");
    assertThat(state.task("ABC-42").orElseThrow().gate().gitStateChanged()).isFalse();
  }

  @Test
  void trustAdoptsWhatChangedAndUnparksTheTask() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .gitTrust(GitTrust.of(Map.of()).withChanged("core.fsmonitor in /r/.git/config added"))
            .build());
    when(git.gitState(List.of(Path.of("/wt"))))
        .thenReturn(Map.of("core.fsmonitor in /r/.git/config", "a1"));

    trusted.trust("ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().gate().gitTrust())
        .isEqualTo(GitTrust.of(Map.of("core.fsmonitor in /r/.git/config", "a1")));
  }
}
