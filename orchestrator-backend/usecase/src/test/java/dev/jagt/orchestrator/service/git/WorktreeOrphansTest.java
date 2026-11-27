package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.task.ArchivedTaskFolder;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.testing.FakeHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class WorktreeOrphansTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {});

  @TempDir Path root;
  private final ConfigService config = mock(ConfigService.class);
  private final GitCommands git = mock(GitCommands.class);
  private final ArchivedTaskFolder folders = mock(ArchivedTaskFolder.class);
  private Path repo;
  private Path registered;
  private StateService state;
  private WorktreeOrphans orphans;

  @BeforeEach
  void setUp() throws IOException {
    repo = Files.createDirectories(root.resolve("demo-repo"));
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.toString())
                .withStateFile(root.resolve("state.json").toString()),
            FakeHost.machine());
    state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    orphans = new WorktreeOrphans(config, state, git, paths, folders);
    registered = root.resolve("ABC-48-demo");
    when(git.run(repo, List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(
            new Processes.Result(0, "worktree " + repo + "\n\nworktree " + registered + "\n", ""));
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigFile.WorktreeConfig.defaults().withCopyGlobs(List.of("**/.env"))));
  }

  @Test
  void findsLeftoverWorktreeAndCountsTheSecretsStillSittingInIt() throws IOException {
    Path orphan = Files.createDirectories(root.resolve("ABC-40-demo"));
    Files.createDirectories(orphan.resolve("app"));
    Files.writeString(orphan.resolve("app").resolve(".env"), "TOKEN=secret");
    Files.writeString(orphan.resolve("app").resolve("key.pem"), "-----BEGIN KEY-----");
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env", "**/*.pem"))));

    var listed = orphans.scan();

    assertThat(listed)
        .singleElement()
        .satisfies(found -> {
          assertThat(found.path()).isEqualTo(orphan);
          assertThat(found.projectKey()).isEqualTo("demo");
          assertThat(found.secretFiles()).isEqualTo(2);
        });
  }

  @Test
  void leavesEveryRepositoryOfLiveTaskAloneNotJustItsFirst() throws IOException {
    Path agentRuns = Files.createDirectories(root.resolve("ABC-1-alpha"));
    Path alsoEdited = Files.createDirectories(root.resolve("ABC-1-demo"));
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(
                    TaskRepo.of("alpha", agentRuns.toString()),
                    TaskRepo.of("demo", alsoEdited.toString())),
                TaskStatus.IN_PROGRESS)
            .build());

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void countsSecretLeftAtTheRootOfAnOrphanTheSameWayItWasCopiedThere() throws IOException {
    Path orphan = Files.createDirectories(root.resolve("ABC-42-demo"));
    Files.writeString(orphan.resolve(".env"), "TOKEN=secret");

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.secretFiles()).isEqualTo(1));
  }

  @Test
  void listsFolderUnderTheDataFolderNoTaskOwns() throws IOException {
    Path left = Files.createDirectories(root.resolve("tasks/ABC-43/worktrees/demo"));

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> {
          assertThat(found.path()).isEqualTo(left);
          assertThat(found.name()).isEqualTo("ABC-43/demo");
          assertThat(found.projectKey()).isEqualTo("demo");
        });
  }

  @Test
  void leavesFolderUnderTheDataFolderOfLiveTaskAlone() throws IOException {
    Path live = Files.createDirectories(root.resolve("tasks/ABC-44/worktrees/demo"));
    state.putTask(
        "ABC-44", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());

    assertThat(orphans.scan()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"-deploy", "-revert"})
  void leavesTheDeployAndRevertWorktreeUnderTheDataFolderOfLiveTaskAlone(String name)
      throws IOException {
    Path live = Files.createDirectories(root.resolve("tasks/ABC-44/worktrees/demo"));
    Files.createDirectories(root.resolve("tasks/ABC-44/worktrees").resolve("demo" + name));
    state.putTask(
        "ABC-44", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());

    assertThat(orphans.scan()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"-deploy", "-revert"})
  void listsTheDeployAndRevertWorktreeUnderTheDataFolderOfTaskThatIsGone(String name)
      throws IOException {
    Path left =
        Files.createDirectories(root.resolve("tasks/ABC-45/worktrees").resolve("demo" + name));

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.path()).isEqualTo(left));
  }

  @Test
  void listsWorktreeLeftInTheOldWorktreesFolderOfTheDataFolderThatNoTaskOwns() throws IOException {
    Path left = Files.createDirectories(root.resolve("worktrees/demo/ABC-50"));

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> {
          assertThat(found.path()).isEqualTo(left);
          assertThat(found.projectKey()).isEqualTo("demo");
        });
  }

  @Test
  void leavesWorktreeInTheOldWorktreesFolderThatLiveTaskRecordedAlone() throws IOException {
    Path live = Files.createDirectories(root.resolve("worktrees/demo/ABC-51"));
    Files.createDirectories(root.resolve("worktrees/demo/ABC-51-deploy"));
    state.putTask(
        "ABC-51", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void shelvesTheTaskFolderOfFinishedTaskOnceItsLeftWorktreeIsDeleted() throws IOException {
    Files.createDirectories(root.resolve("tasks/ABC-52/worktrees/demo"));
    Path other = Files.createDirectories(root.resolve("tasks/ABC-99/worktrees/demo"));
    state.putTask(
        "ABC-99", TaskState.builder("demo", other.toString(), TaskStatus.IN_PROGRESS).build());
    when(git.run(repo, List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(new Processes.Result(0, "worktree " + repo + "\n", ""));

    orphans.delete("ABC-52/demo");

    verify(folders).shelve("ABC-52", null);
  }

  @Test
  void shelvesTheTaskFolderOfFinishedTaskOnceItsRegisteredWorktreeIsRemoved() throws IOException {
    Path left = Files.createDirectories(root.resolve("tasks/ABC-54/worktrees/demo"));
    when(git.run(repo, List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(
            new Processes.Result(0, "worktree " + repo + "\n\nworktree " + left + "\n", ""));
    when(git.run(repo, List.of("git", "worktree", "remove", "--force", left.toString())))
        .thenReturn(new Processes.Result(0, "", ""));

    orphans.delete("ABC-54/demo");

    verify(folders).shelve("ABC-54", null);
  }

  @Test
  void keepsTheTaskFolderOfLiveTaskWhenAnotherOfItsWorktreesIsDeleted() throws IOException {
    Path live = Files.createDirectories(root.resolve("tasks/ABC-53/worktrees/api"));
    Files.createDirectories(root.resolve("tasks/ABC-53/worktrees/demo"));
    state.putTask(
        "ABC-53", TaskState.builder("api", live.toString(), TaskStatus.IN_PROGRESS).build());
    when(git.run(repo, List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(new Processes.Result(0, "worktree " + repo + "\n", ""));

    orphans.delete("ABC-53/demo");

    verify(folders, never()).shelve(any(), any());
  }

  @Test
  void leavesTheWorktreeOfLiveTaskAlone() throws IOException {
    Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
    state.putTask(
        "ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void ignoresCheckoutSomebodyCutByHandOutsideJagt() throws IOException {
    Path byHand = Files.createDirectories(root.resolve("ABC-46-demo"));
    Files.createDirectories(repo.resolve(".git/worktrees/ABC-46-demo"));
    Files.writeString(
        byHand.resolve(".git"), "gitdir: " + repo.resolve(".git/worktrees/ABC-46-demo") + "\n");
    Files.writeString(byHand.resolve(".env"), "TOKEN=secret");

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void reportsCheckoutJagtCutItselfAndNobodyRetired() throws IOException {
    Path cutByJagt = Files.createDirectories(root.resolve("ABC-47-demo"));
    Files.createDirectories(repo.resolve(".git/worktrees/ABC-47-demo"));
    Files.writeString(
        cutByJagt.resolve(".git"), "gitdir: " + repo.resolve(".git/worktrees/ABC-47-demo") + "\n");
    Files.writeString(cutByJagt.resolve("task_context.md"), "the task");

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.path()).isEqualTo(cutByJagt));
  }

  @Test
  void deletesTheOrphanItIsNamedForEvenWhereItHoldsCopiedSecret() throws IOException {
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(".env"), "TOKEN=secret");

    when(git.run(repo, List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(new Processes.Result(0, "worktree " + repo + "\n", ""));

    String said = orphans.delete("ABC-48-demo");

    assertThat(orphan).doesNotExist();
    assertThat(said).isEqualTo("deleted " + orphan);
  }

  @Test
  void refusesToDeleteDirectoryThatIsNoOrphan() throws IOException {
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(".env"), "TOKEN=secret");

    assertThatThrownBy(() -> orphans.delete("demo-repo"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(repo).exists();
    assertThat(orphan).exists();
  }

  @Test
  void saysWhyAnOrphanCouldNotBeDeletedAndKeepsIt() throws IOException {
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(".env"), "TOKEN=secret");

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
  void removesRegisteredOrphanThroughGitInItsRepositoryAndNothingElse() throws IOException {
    Path orphan = Files.createDirectories(registered);
    Files.writeString(orphan.resolve(".git"), "gitdir: " + repo + "/.git/worktrees/x");
    when(git.run(repo, List.of("git", "worktree", "remove", "--force", orphan.toString())))
        .thenReturn(new Processes.Result(0, "", ""));

    String said = orphans.delete("ABC-48-demo");

    assertThat(said).isEqualTo("deleted " + orphan);
    verify(git).run(repo, List.of("git", "worktree", "remove", "--force", orphan.toString()));
    verify(git, never()).run(repo, List.of("git", "worktree", "prune"));
  }

  @Test
  void keepsRegisteredOrphanAndSaysWhyWhereGitCannotRemoveIt() throws IOException {
    Path orphan = Files.createDirectories(registered);
    Files.writeString(orphan.resolve(".git"), "gitdir: " + repo + "/.git/worktrees/x");
    when(git.run(repo, List.of("git", "worktree", "remove", "--force", orphan.toString())))
        .thenReturn(new Processes.Result(128, "", "fatal: locked\n"));

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
  void reportsDeployDirectoryHoldingNoCheckoutWhileItsTaskIsStillAlive() throws IOException {
    Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
    Path residue = Files.createDirectories(root.resolve("ABC-41-deploy"));
    Files.createDirectories(residue.resolve(".idea"));
    state.putTask(
        "ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.path()).isEqualTo(residue));
  }

  @Test
  void reportsDeployDirectoryWhoseCheckoutWasUnregisteredButNeverDeleted() throws IOException {
    final Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
    Path unregistered = Files.createDirectories(root.resolve("ABC-41-deploy"));
    Files.writeString(
        unregistered.resolve(".git"),
        "gitdir: " + repo.resolve(".git/worktrees/ABC-41-deploy") + "\n");
    Files.writeString(unregistered.resolve(".env"), "TOKEN=secret");
    state.putTask(
        "ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.secretFiles()).isEqualTo(1));
  }

  @Test
  void leavesTheDeployDirectoryOfLiveTaskWhileItHoldsRealCheckout() throws IOException {
    Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
    Files.createDirectories(root.resolve("ABC-41-deploy").resolve(".git"));
    state.putTask(
        "ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void leavesTheDeployDirectoryOfLiveTaskWhoseCheckoutPointerCannotBeRead() throws IOException {
    Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
    Path pointer =
        Files.writeString(
            Files.createDirectories(root.resolve("ABC-41-deploy")).resolve(".git"),
            "gitdir: " + repo.resolve(".git/worktrees/ABC-41-deploy") + "\n");
    Files.setPosixFilePermissions(pointer, PosixFilePermissions.fromString("---------"));
    state.putTask(
        "ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build());

    assertThat(orphans.scan()).isEmpty();
  }

  @Test
  void stillFindsLeftoverWhenLiveTaskHasNotCutWorktreeInOneOfItsRepositories() throws IOException {
    Path orphan = Files.createDirectories(root.resolve("ABC-40-demo"));
    state.putTask(
        "ABC-41",
        TaskState.builder(List.of(TaskRepo.of("demo", null)), TaskStatus.IN_PROGRESS).build());

    assertThat(orphans.scan())
        .singleElement()
        .satisfies(found -> assertThat(found.path()).isEqualTo(orphan));
  }
}
