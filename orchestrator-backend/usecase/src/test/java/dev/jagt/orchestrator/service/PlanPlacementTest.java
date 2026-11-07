package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class PlanPlacementTest {

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private final NewTaskWorktrees worktrees = mock(NewTaskWorktrees.class);
  private final RetiredWorktrees retired = mock(RetiredWorktrees.class);
  private StateService state;
  private PlanPlacement placement;

  @BeforeEach
  void setUp() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    placement = new PlanPlacement(state, new TaskArtifacts(state), config, worktrees, retired);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
  }

  @Test
  void placesTheTaskInTheRepositoriesItsPlanChangesAndCarriesItsDocumentsOver() throws Exception {
    Path scratch = Files.createDirectories(root.resolve("scratch/ABC-9"));
    Files.writeString(
        scratch.resolve("plan.md"),
        "## Repositories\n- [changed] api\n- [unaffected: no UI] docs\n- [changed] web\n");
    Files.writeString(scratch.resolve("task_request.md"), "split the mailer");
    Files.createDirectories(root.resolve("ABC-9-api"));
    List<NewRepo> cut =
        List.of(
            new NewRepo(
                "api",
                new ProjectConfig("/api", "origin/main", "dev", List.of()),
                Path.of("/api"),
                root.resolve("ABC-9-api"),
                root.resolve(".git"),
                "origin/main",
                "",
                true),
            new NewRepo(
                "web",
                new ProjectConfig("/web", "origin/main", "dev", List.of()),
                Path.of("/web"),
                root.resolve("ABC-9-web"),
                root.resolve(".git"),
                "origin/main",
                "",
                false));
    when(worktrees.place(any(), any(), any()))
        .thenAnswer(
            call -> {
              Consumer<List<NewRepo>> registration = call.getArgument(2);
              registration.accept(cut);
              return cut;
            });
    TaskRepo scratchRepo = TaskRepo.of(null, scratch.toString());
    state.putTask(
        "ABC-9",
        TaskState.builder(List.of(scratchRepo), TaskStatus.PLAN_PENDING).title("Mailer").build());

    boolean placed = placement.place("ABC-9");

    assertThat(placed).isTrue();
    ArgumentCaptor<NewTask> request = ArgumentCaptor.forClass(NewTask.class);
    verify(worktrees).place(request.capture(), any(), any());
    assertThat(request.getValue().projectKeys()).containsExactly("api", "web");
    assertThat(request.getValue().title()).isEqualTo("Mailer");
    assertThat(state.task("ABC-9").orElseThrow().projects()).containsExactly("api", "web");
    assertThat(root.resolve("ABC-9-api/plan.md")).content().contains("- [changed] web");
    assertThat(root.resolve("ABC-9-api/task_request.md")).hasContent("split the mailer");
    verify(retired)
        .remove("ABC-9", List.of(scratchRepo), ConfigService.ConfigFile.defaults().projects());
  }

  @Test
  void trustsTheGitStateOfTheWorktreesItWasPlacedIn() throws Exception {
    Path scratch = Files.createDirectories(root.resolve("scratch/ABC-9"));
    Files.writeString(scratch.resolve("plan.md"), "## Repositories\n- [changed] api\n");
    Files.createDirectories(root.resolve("ABC-9-api"));
    List<NewRepo> cut =
        List.of(
            new NewRepo(
                "api",
                new ProjectConfig("/api", "origin/main", "dev", List.of()),
                Path.of("/api"),
                root.resolve("ABC-9-api"),
                root.resolve(".git"),
                "origin/main",
                "",
                true));
    when(worktrees.place(any(), any(), any()))
        .thenAnswer(
            call -> {
              Consumer<List<NewRepo>> registration = call.getArgument(2);
              registration.accept(cut);
              return cut;
            });
    GitTrust trust = GitTrust.of(Map.of("core.hooksPath", "hooks"));
    when(worktrees.trust(cut)).thenReturn(trust);
    state.putTask(
        "ABC-9",
        TaskState.builder(List.of(TaskRepo.of(null, scratch.toString())), TaskStatus.PLAN_PENDING)
            .build());

    placement.place("ABC-9");

    assertThat(state.task("ABC-9").orElseThrow().gitTrust()).isEqualTo(trust);
  }

  @Test
  void keepsTheTypedBaseBranchOfThePlacedTask() throws Exception {
    Path scratch = Files.createDirectories(root.resolve("scratch/ABC-9"));
    Files.writeString(scratch.resolve("plan.md"), "## Repositories\n- [changed] api\n");
    Files.createDirectories(root.resolve("ABC-9-api"));
    List<NewRepo> cut =
        List.of(
            new NewRepo(
                "api",
                new ProjectConfig("/api", "origin/main", "dev", List.of()),
                Path.of("/api"),
                root.resolve("ABC-9-api"),
                root.resolve(".git"),
                "feature/parent",
                "",
                true));
    when(worktrees.place(any(), any(), any()))
        .thenAnswer(
            call -> {
              Consumer<List<NewRepo>> registration = call.getArgument(2);
              registration.accept(cut);
              return cut;
            });
    state.putTask(
        "ABC-9",
        TaskState.builder(List.of(TaskRepo.of(null, scratch.toString())), TaskStatus.PLAN_PENDING)
            .baseBranch("origin/feature/parent")
            .build());

    placement.place("ABC-9");

    assertThat(state.task("ABC-9").orElseThrow().baseBranch()).isEqualTo("feature/parent");
  }

  @Test
  void leavesTheScratchFolderWhereACutFails() throws Exception {
    Path scratch = Files.createDirectories(root.resolve("scratch/ABC-9"));
    Files.writeString(scratch.resolve("plan.md"), "## Repositories\n- [changed] api\n");
    when(worktrees.place(any(), any(), any())).thenThrow(new IllegalStateException("git failed"));
    state.putTask(
        "ABC-9",
        TaskState.builder(List.of(TaskRepo.of(null, scratch.toString())), TaskStatus.PLAN_PENDING)
            .build());

    assertThatThrownBy(() -> placement.place("ABC-9")).isInstanceOf(IllegalStateException.class);

    verify(retired, never()).remove(any(), any(), any());
    assertThat(state.task("ABC-9").orElseThrow().unplaced()).isTrue();
  }

  @Test
  void placesNothingOfATaskThatAlreadyHoldsARepository() {
    state.putTask("ABC-9", TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING).build());

    boolean placed = placement.place("ABC-9");

    assertThat(placed).isFalse();
    verifyNoInteractions(worktrees);
  }

  @Test
  void refusesAPlanThatChangesNoRepository() throws Exception {
    Path scratch = Files.createDirectories(root.resolve("scratch/ABC-9"));
    Files.writeString(
        scratch.resolve("plan.md"), "## Repositories\n- [unaffected: nothing to do] api\n");
    state.putTask(
        "ABC-9",
        TaskState.builder(List.of(TaskRepo.of(null, scratch.toString())), TaskStatus.PLAN_PENDING)
            .build());

    assertThatThrownBy(() -> placement.place("ABC-9")).isInstanceOf(Refusal.class);

    verifyNoInteractions(worktrees);
  }
}
