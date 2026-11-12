package dev.jagt.orchestrator.capability.done;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.capability.TaskViewer;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.port.LineChange;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.KeptTicketTexts;
import dev.jagt.orchestrator.service.PlanBaseline;
import dev.jagt.orchestrator.service.RetiredWorktrees;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskArtifacts;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskRetirementTest {

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private final TaskViewer viewer = mock(TaskViewer.class);
  private final RetiredWorktrees worktrees = mock(RetiredWorktrees.class);
  private OrchestratorPaths paths;
  private StateService state;
  private TaskRetirement retirement;

  @BeforeEach
  void retirementOverStateInTheTempRoot() {
    paths = orchestratorPaths().withRoot(root).build();
    state = stateService().withRoot(root).build();
    retirement =
        new TaskRetirement(
            state,
            config,
            worktrees,
            viewer,
            new KeptTaskFiles(
                new KeptTicketTexts(paths),
                new PlanBaseline(
                    paths, new TaskArtifacts(state), (earlier, later) -> LineChange.none())));
  }

  @Test
  void killsTheSessionBeforeDeletingTheDirectoryItIsRunningIn() {
    state.putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.DONE).alias("a1").build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    String result = retirement.retire("a1");

    var order = inOrder(viewer, worktrees);
    order.verify(viewer).killWindows("ABC-1");
    order.verify(worktrees).remove(anyString(), any(), any());
    assertThat(state.task("ABC-1")).isEmpty();
    assertThat(result).contains("Branch 'ABC-1' was kept").doesNotContain("agents window");
  }

  @Test
  void namesNoBranchForTaskThatNeverHadRepository() {
    state.putTask(
        "ABC-1",
        TaskState.builder(List.of(TaskRepo.of(null, "/scratch/ABC-1")), TaskStatus.PLAN_PENDING)
            .alias("a1")
            .build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    String result = retirement.retire("a1");

    assertThat(result).contains("scratch folder deleted").doesNotContain("Branch");
  }

  @Test
  void saysTheWorktreeStayedOnDiskWhenItsProjectIsGoneFromConfig() {
    state.putTask("ABC-1", TaskState.builder("gone", "/wt", TaskStatus.DONE).alias("a1").build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(worktrees.remove(anyString(), any(), any())).thenReturn(true);
    String result = retirement.retire("a1");

    assertThat(result).contains("worktree left on disk: project missing from jagt.yml");
    assertThat(state.task("ABC-1")).isEmpty();
  }

  @Test
  void saysTheAgentsWindowWasClosedWithTheLastTask() {
    state.putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.DONE).alias("a1").build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(viewer.closeIfNoTasksLeft()).thenReturn(true);
    String result = retirement.retire("a1");

    assertThat(result).endsWith(". Last task gone — the agents window was closed.");
  }

  @Test
  void refusesTaskThatIsNotThereNamingItAndKillsNothing() {
    assertThatThrownBy(() -> retirement.retire("ABC-9"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("No task ABC-9")
        .extracting("code")
        .isEqualTo(Refusal.Code.NO_SUCH_TASK);
    verifyNoInteractions(viewer);
  }

  @Test
  void leavesNeitherTicketTextNorPlanBaselineForLaterTaskOnTheSameId()
      throws java.io.IOException {
    state.putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.DONE).alias("a1").build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    java.nio.file.Files.createDirectories(paths.ticketTexts());
    java.nio.file.Files.writeString(
        paths.ticketTexts().resolve("ABC-1.md"), "Widget layout is off");
    java.nio.file.Files.createDirectories(paths.planBaselines());
    java.nio.file.Files.writeString(paths.planBaselines().resolve("ABC-1.md"), "old plan");

    retirement.retire("a1");

    assertThat(paths.ticketTexts().resolve("ABC-1.md")).doesNotExist();
    assertThat(paths.planBaselines().resolve("ABC-1.md")).doesNotExist();
  }
}
