package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class WorktreeOrphansTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @Test
  void findsLeftoverWorktreeAndCountsTheSecretsStillSittingInIt(@TempDir Path root)
      throws IOException {
    final Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-40-demo"));
    Files.createDirectories(orphan.resolve("app"));
    Files.writeString(orphan.resolve("app").resolve(".env"), "TOKEN=secret");
    Files.writeString(orphan.resolve("app").resolve("key.pem"), "-----BEGIN KEY-----");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env", "**/*.pem"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    var listed = orphans.scan();

    assertThat(listed)
        .singleElement()
        .satisfies(
            found -> {
              assertThat(found.path()).isEqualTo(orphan);
              assertThat(found.projectKey()).isEqualTo("demo");
              assertThat(found.secretFiles()).isEqualTo(2);
            });
  }

  @Test
  void leavesEveryRepositoryOfLiveTaskAloneNotJustItsFirst(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path agentRuns = Files.createDirectories(root.resolve("ABC-1-alpha"));
    Path alsoEdited = Files.createDirectories(root.resolve("ABC-1-demo"));
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(
                    TaskRepo.of("alpha", agentRuns.toString()),
                    TaskRepo.of("demo", alsoEdited.toString())),
                TaskStatus.IN_PROGRESS)
            .build());
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void countsSecretLeftAtTheRootOfAnOrphanTheSameWayItWasCopiedThere(@TempDir Path root)
      throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-42-demo"));
    Files.writeString(orphan.resolve(".env"), "TOKEN=secret");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.secretFiles()).isEqualTo(1));
  }

  @Test
  void leavesTheWorktreeOfLiveTaskAlone(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask(
        "ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void ignoresCheckoutSomebodyCutByHandOutsideJagt(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path byHand = Files.createDirectories(root.resolve("ABC-46-demo"));
    Files.createDirectories(repo.resolve(".git/worktrees/ABC-46-demo"));
    Files.writeString(
        byHand.resolve(".git"), "gitdir: " + repo.resolve(".git/worktrees/ABC-46-demo") + "\n");
    Files.writeString(byHand.resolve(".env"), "TOKEN=secret");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void reportsCheckoutJagtCutItselfAndNobodyRetired(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path cutByJagt = Files.createDirectories(root.resolve("ABC-47-demo"));
    Files.createDirectories(repo.resolve(".git/worktrees/ABC-47-demo"));
    Files.writeString(
        cutByJagt.resolve(".git"), "gitdir: " + repo.resolve(".git/worktrees/ABC-47-demo") + "\n");
    Files.writeString(cutByJagt.resolve("task_context.md"), "the task");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.path()).isEqualTo(cutByJagt));
  }

  @Test
  void deletesTheOrphanItIsNamedForEvenWhereItHoldsCopiedSecret(@TempDir Path root)
      throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(".env"), "TOKEN=secret");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    when(git.run(repo, List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(new Processes.Result(0, "worktree " + repo + "\n", ""));

    String said = orphans.delete("ABC-48-demo");

    assertThat(orphan).doesNotExist();
    assertThat(said).isEqualTo("deleted " + orphan);
  }

  @Test
  void refusesToDeleteDirectoryThatIsNoOrphan(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(".env"), "TOKEN=secret");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThatThrownBy(() -> orphans.delete("demo-repo"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(repo).exists();
    assertThat(orphan).exists();
  }

  @Test
  void saysWhyAnOrphanCouldNotBeDeletedAndKeepsIt(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(".env"), "TOKEN=secret");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    GitCommands git = mock(GitCommands.class);
    final WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    Files.createDirectories(orphan.resolve("sub"));
    Files.writeString(orphan.resolve("sub").resolve("a.txt"), "work");
    Files.setPosixFilePermissions(
        orphan.resolve("sub"), PosixFilePermissions.fromString("r-xr-xr-x"));

    when(git.run(repo, List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(new Processes.Result(0, "worktree " + repo + "\n", ""));

    String said = orphans.delete("ABC-48-demo");

    assertThat(said).startsWith("could not delete " + orphan + ": ");
    assertThat(orphan.resolve("sub").resolve("a.txt")).exists();
  }

  @Test
  void removesRegisteredOrphanThroughGitInItsRepositoryAndNothingElse(@TempDir Path root)
      throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(".git"), "gitdir: " + repo + "/.git/worktrees/x");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    GitCommands git = mock(GitCommands.class);
    when(git.run(repo, List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(
            new Processes.Result(0, "worktree " + repo + "\n\nworktree " + orphan + "\n", ""));
    when(git.run(repo, List.of("git", "worktree", "remove", "--force", orphan.toString())))
        .thenReturn(new Processes.Result(0, "", ""));
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    String said = orphans.delete("ABC-48-demo");

    assertThat(said).isEqualTo("deleted " + orphan);
    verify(git).run(repo, List.of("git", "worktree", "remove", "--force", orphan.toString()));
    verify(git, never()).run(repo, List.of("git", "worktree", "prune"));
  }

  @Test
  void keepsRegisteredOrphanAndSaysWhyWhereGitCannotRemoveIt(@TempDir Path root)
      throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(".git"), "gitdir: " + repo + "/.git/worktrees/x");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    GitCommands git = mock(GitCommands.class);
    when(git.run(repo, List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(
            new Processes.Result(0, "worktree " + repo + "\n\nworktree " + orphan + "\n", ""));
    when(git.run(repo, List.of("git", "worktree", "remove", "--force", orphan.toString())))
        .thenReturn(new Processes.Result(128, "", "fatal: locked\n"));
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    String said = orphans.delete("ABC-48-demo");

    assertThat(said).isEqualTo("could not remove " + orphan + ": fatal: locked");
    assertThat(orphan).exists();
  }

  @Test
  void recognisesBothTaskWorktreeAndAnAbandonedDeployWorktree() {
    List<String> onDisk =
        List.of(
            "ABC-40-demo",
            "ABC-41-demo",
            "ABC-41-deploy",
            "demo-repo",
            "something-else",
            "XYZ-1-other");

    Set<String> orphans =
        WorktreeOrphans.orphanNames(
            onDisk, "demo", Set.of("ABC-41-demo", "ABC-41-deploy"), name -> true);

    assertThat(orphans).containsExactly("ABC-40-demo");
  }

  @Test
  void treatsDeployLeftoverOfRetiredTaskAsAnOrphan() {
    Set<String> orphans =
        WorktreeOrphans.orphanNames(
            List.of("ABC-40-deploy", "demo-repo"), "demo", Set.of(), name -> true);

    assertThat(orphans).containsExactly("ABC-40-deploy");
  }

  @Test
  void treatsRevertLeftoverAsAnOrphanJustLikeDeployOne() {
    Set<String> orphans =
        WorktreeOrphans.orphanNames(
            List.of("ABC-40-revert", "demo-repo"), "demo", Set.of(), name -> true);

    assertThat(orphans).containsExactly("ABC-40-revert");
  }

  @Test
  void reportsDeployDirectoryHoldingNoCheckoutWhileItsTaskIsStillAlive(@TempDir Path root)
      throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
    Path residue = Files.createDirectories(root.resolve("ABC-41-deploy"));
    Files.createDirectories(residue.resolve(".idea"));
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask(
        "ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.path()).isEqualTo(residue));
  }

  @Test
  void reportsDeployDirectoryWhoseCheckoutWasUnregisteredButNeverDeleted(@TempDir Path root)
      throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    final Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
    Path unregistered = Files.createDirectories(root.resolve("ABC-41-deploy"));
    Files.writeString(
        unregistered.resolve(".git"),
        "gitdir: " + repo.resolve(".git/worktrees/ABC-41-deploy") + "\n");
    Files.writeString(unregistered.resolve(".env"), "TOKEN=secret");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask(
        "ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.secretFiles()).isEqualTo(1));
  }

  @Test
  void leavesTheDeployDirectoryOfLiveTaskWhileItHoldsRealCheckout(@TempDir Path root)
      throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
    Files.createDirectories(root.resolve("ABC-41-deploy").resolve(".git"));
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask(
        "ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void stillFindsLeftoverWhenLiveTaskHasNotCutWorktreeInOneOfItsRepositories(@TempDir Path root)
      throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-40-demo"));
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask(
        "ABC-41",
        TaskState.builder(List.of(TaskRepo.of("demo", null)), TaskStatus.IN_PROGRESS).build());
    GitCommands git = mock(GitCommands.class);
    WorktreeOrphans orphans = new WorktreeOrphans(config, state, git);

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.path()).isEqualTo(orphan));
  }
}
