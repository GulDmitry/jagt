package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.git.GitWorktrees;
import dev.jagt.orchestrator.service.plan.Unplaceable;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.launch.NewTask;
import dev.jagt.orchestrator.task.repo.BranchStrategy;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.NewRepo;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class NewTaskWorktreesTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private final GitWorktrees git = mock(GitWorktrees.class);
  private final WorktreeSetup setup = mock(WorktreeSetup.class);
  private StateService state;
  private NewTaskWorktrees worktrees;
  private ConfigService.ConfigFile oneProject;
  private ConfigService.ConfigFile registryMain;
  private ConfigService.ConfigFile registryRelease;
  private ConfigService.ConfigFile apiWeb;
  private ConfigService.ConfigFile apiWebDev;
  private TaskState attaching;
  private final NewRepo apiRepo =
      new NewRepo(
          "api",
          new ProjectConfig("/api", "origin/main", null, List.of()),
          Path.of("/api"),
          Path.of("/ABC-1-api"),
          Path.of("/api/.git"),
          "origin/main",
          "git@host:api.git",
          true);

  @BeforeEach
  void setUp() {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.resolve("data").toString())
            .withStateFile(root.resolve("state.json").toString());
    OrchestratorPaths paths = new OrchestratorPaths(properties);
    state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    worktrees = new NewTaskWorktrees(config, state, git, setup, paths);
    oneProject =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                Map.of(
                    "proj",
                    new ProjectConfig(
                        root.resolve("repo").toString(), "origin/main", null, List.of())));
    registryMain =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                new LinkedHashMap<>(
                    Map.of(
                        "api",
                        new ProjectConfig(
                            root.resolve("api-repo").toString(), "origin/main", null, List.of()),
                        "registry",
                        new ProjectConfig(
                            root.resolve("registry-repo").toString(),
                            "origin/main",
                            null,
                            List.of()))));
    registryRelease =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                new LinkedHashMap<>(
                    Map.of(
                        "api",
                        new ProjectConfig(
                            root.resolve("api-repo").toString(), "origin/main", null, List.of()),
                        "registry",
                        new ProjectConfig(
                            root.resolve("registry-repo").toString(),
                            "origin/release",
                            null,
                            List.of()))));
    apiWeb =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                new LinkedHashMap<>(
                    Map.of(
                        "api",
                        new ProjectConfig(
                            root.resolve("api-repo").toString(), "origin/main", null, List.of()),
                        "web",
                        new ProjectConfig(
                            root.resolve("web-repo").toString(),
                            "origin/release",
                            null,
                            List.of()))));
    apiWebDev =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                new LinkedHashMap<>(
                    Map.of(
                        "api", new ProjectConfig("/api", "origin/main", "dev", List.of()),
                        "web", new ProjectConfig("/web", "origin/main", "dev", List.of()))));
    attaching =
        TaskState.builder(
                "api", root.resolve("data/worktrees/api/ABC-7").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repoRequest("registry")
            .build();
  }

  @Test
  void stepsOverWrittenNameLiveTaskAlreadyTookRatherThanRefusingIt() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    state.putTask(
        "split-the-mailer",
        TaskState.builder("proj", "/first", TaskStatus.IN_PROGRESS).alias("a1").build());

    assertThat(worktrees.freeTaskName("split-the-mailer", List.of("proj")))
        .isEqualTo("split-the-mailer-2");
  }

  @ParameterizedTest
  @CsvSource({"false, false, FRESH", "true, false, RECREATE", "true, true, RESUME"})
  void intakeRecreatesOnlyLeftoverBranchHoldingNoWorkOfItsOwn(
      boolean exists, boolean holdsWork, BranchStrategy expected) {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of("api", new ProjectConfig("/api", "origin/main", "dev", List.of()))));
    when(git.branchExists(Path.of("/api"), "ABC-1")).thenReturn(exists);
    when(git.holdsOwnCommits(Path.of("/api"), "ABC-1", "origin/main")).thenReturn(holdsWork);

    assertThat(worktrees.strategyForExisting("ABC-1", "api")).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"../escape", "a b", "-lead", "feature/", "feature//x", "x.lock"})
  void rejectsTaskIdBeforeTouchingGitWhenGitWouldRefuseItAsBranch(String unsafeTaskId) {
    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder(unsafeTaskId, "proj").build(),
                ConfigService.ConfigFile.defaults(),
                BranchStrategy.FRESH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not a branch name");
    verifyNoInteractions(git);
  }

  @ParameterizedTest
  @CsvSource({"main,", "Main,", "origin/main,", "dev,", "release,release"})
  void refusesTaskWhoseBranchIsSharedOneBeforeTouchingGit(String taskId, String baseBranch) {
    ConfigService.ConfigFile configured =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                Map.of(
                    "proj",
                    new ProjectConfig(
                        root.resolve("repo").toString(),
                        "origin/main",
                        "refs/heads/dev",
                        List.of())));

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder(taskId, "proj").baseBranch(baseBranch).build(),
                configured,
                BranchStrategy.RESUME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("would be the shared branch");
    verifyNoInteractions(git);
  }

  @Test
  void refusesBaseBranchGitWouldReadAsAnOptionBeforeTouchingGit() {

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("ABC-42", "proj").baseBranch("--upload-pack=touch").build(),
                oneProject,
                BranchStrategy.FRESH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not a branch name: it starts with '-'");
    verifyNoInteractions(git);
  }

  @Test
  void cutsOneFlatWorktreeUnderTheDataFolderWhileTheRepositoryStaysWhereItIs() {

    worktrees.cut(
        NewTask.builder("feature/ABC-42", "proj").build(), oneProject, BranchStrategy.FRESH);

    verify(git)
        .createWorktree(
            root.resolve("repo"),
            root.resolve("data/worktrees/proj/feature-ABC-42"),
            "feature/ABC-42",
            "origin/main",
            BranchStrategy.FRESH);
  }

  @Test
  void refusesSecondTaskWhoseBranchBecomesTheDirectoryOfLiveOne() {
    state.putTask(
        "feature/ABC-42",
        TaskState.builder("proj", "/first", TaskStatus.IN_PROGRESS).alias("f1").build());

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("feature-ABC-42", "proj").build(),
                ConfigService.ConfigFile.defaults(),
                BranchStrategy.FRESH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("both become the directory feature-ABC-42");
  }

  @Test
  void refusesTheTwentyFifthTaskAsTheStateNotTheCall() throws Exception {
    Files.writeString(
        root.resolve("state.json"),
        """
        {"tasks":{"T01":{},"T02":{},"T03":{},"T04":{},"T05":{},"T06":{},"T07":{},"T08":{},
        "T09":{},"T10":{},"T11":{},"T12":{},"T13":{},"T14":{},"T15":{},"T16":{},
        "T17":{},"T18":{},"T19":{},"T20":{},"T21":{},"T22":{},"T23":{},"T24":{}}}\
        """);

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("ABC-99", "proj").build(),
                ConfigService.ConfigFile.defaults(),
                BranchStrategy.FRESH))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE))
        .hasMessageContaining("24 tasks are already open");
  }

  @Test
  void cutsTheTwentyFourthTask() throws Exception {
    Files.writeString(
        root.resolve("state.json"),
        """
        {"tasks":{"T01":{},"T02":{},"T03":{},"T04":{},"T05":{},"T06":{},"T07":{},"T08":{},
        "T09":{},"T10":{},"T11":{},"T12":{},"T13":{},"T14":{},"T15":{},"T16":{},
        "T17":{},"T18":{},"T19":{},"T20":{},"T21":{},"T22":{},"T23":{}}}\
        """);

    worktrees.cut(NewTask.builder("ABC-99", "proj").build(), oneProject, BranchStrategy.FRESH);

    verify(git)
        .createWorktree(
            root.resolve("repo"),
            root.resolve("data/worktrees/proj/ABC-99"),
            "ABC-99",
            "origin/main",
            BranchStrategy.FRESH);
  }

  @Test
  void removesFreshWorktreeAndBranchWhenContextSetupFailsAfterCheckout() {
    doThrow(new IllegalStateException("provisioning failed")).when(setup).fill(any(), any(), any());

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("ABC-9", "proj").build(), oneProject, BranchStrategy.FRESH))
        .isInstanceOf(IllegalStateException.class);

    verify(git)
        .removeWorktree(root.resolve("repo"), root.resolve("data/worktrees/proj/ABC-9"), "ABC-9");
  }

  @Test
  void keepsResumedBranchWhenTheUnwindRemovesItsWorktree() {
    doThrow(new IllegalStateException("provisioning failed")).when(setup).fill(any(), any(), any());

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("ABC-4", "proj").build(), oneProject, BranchStrategy.RESUME))
        .isInstanceOf(IllegalStateException.class);

    verify(git)
        .removeWorktree(root.resolve("repo"), root.resolve("data/worktrees/proj/ABC-4"), null);
  }

  @Test
  void cutsOneWorktreePerProjectFromEachProjectsOwnBase() {

    List<NewRepo> repos =
        worktrees.cut(
            NewTask.builder("ABC-7", "api").alsoIn(List.of("web")).build(),
            apiWeb,
            BranchStrategy.FRESH);

    assertThat(repos).extracting(NewRepo::project).containsExactly("api", "web");
    verify(git)
        .createWorktree(
            root.resolve("api-repo"),
            root.resolve("data/worktrees/api/ABC-7"),
            "ABC-7",
            "origin/main",
            BranchStrategy.FRESH);
    verify(git)
        .createWorktree(
            root.resolve("web-repo"),
            root.resolve("data/worktrees/web/ABC-7"),
            "ABC-7",
            "origin/release",
            BranchStrategy.FRESH);
  }

  @Test
  void unwindsTheWorktreesItAlreadyCutWhenLaterRepositoryFails() {
    doNothing()
        .doThrow(new IllegalStateException("second repo failed"))
        .when(setup)
        .fill(any(), any(), any());

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("ABC-8", "api").alsoIn(List.of("web")).build(),
                apiWeb,
                BranchStrategy.FRESH))
        .isInstanceOf(IllegalStateException.class);

    verify(git)
        .removeWorktree(
            root.resolve("api-repo"), root.resolve("data/worktrees/api/ABC-8"), "ABC-8");
    verify(git)
        .removeWorktree(
            root.resolve("web-repo"), root.resolve("data/worktrees/web/ABC-8"), "ABC-8");
  }

  @Test
  void refusesTheWholeTaskWhenOneOfItsProjectsIsUnknown() {
    ConfigService.ConfigFile configured =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                Map.of(
                    "api",
                    new ProjectConfig(
                        root.resolve("api-repo").toString(), "origin/main", null, List.of())));

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("ABC-6", "api").alsoIn(List.of("nope")).build(),
                configured,
                BranchStrategy.FRESH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown project 'nope'");

    verify(git, never()).createWorktree(any(), any(), any(), any(), any());
  }

  @Test
  void cutsTheWorktreeFromTheBranchTheHumanNamed() {
    when(git.remoteBranchExists(any(), eq("feature/parent"))).thenReturn(true);

    worktrees.cut(
        NewTask.builder("ABC-3", "proj").baseBranch("origin/feature/parent").build(),
        oneProject,
        BranchStrategy.FRESH);

    verify(git)
        .createWorktree(
            root.resolve("repo"),
            root.resolve("data/worktrees/proj/ABC-3"),
            "ABC-3",
            "feature/parent",
            BranchStrategy.FRESH);
  }

  @Test
  void cutsTheWorktreeFromTheBranchNamedUnderTheProjectsOwnRemote() {
    ConfigService.ConfigFile configured =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                Map.of(
                    "proj",
                    TestProjectConfigBuilder.projectConfig()
                        .withPath(root.resolve("repo").toString())
                        .withBaseBranch("upstream/main")
                        .withDeployBranch(null)
                        .withRemote("upstream")
                        .build()));
    when(git.remoteBranchExists(any(), eq("feature/parent"))).thenReturn(true);

    worktrees.cut(
        NewTask.builder("ABC-3", "proj").baseBranch("upstream/feature/parent").build(),
        configured,
        BranchStrategy.FRESH);

    verify(git)
        .createWorktree(
            root.resolve("repo"),
            root.resolve("data/worktrees/proj/ABC-3"),
            "ABC-3",
            "feature/parent",
            BranchStrategy.FRESH);
  }

  @Test
  void refusesBaseBranchOriginDoesNotHaveBeforeCreatingAnything() {
    when(git.remoteBranchExists(any(), eq("feature/typo"))).thenReturn(false);

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("ABC-5", "proj").baseBranch("feature/typo").build(),
                oneProject,
                BranchStrategy.FRESH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not exist on proj's origin");

    verify(git, never()).createWorktree(any(), any(), any(), any(), any());
  }

  @Test
  void namesTheRepositoryWhoseOriginIsMissingTheBaseBranch() {
    when(git.remoteBranchExists(root.resolve("api-repo"), "feature/parent")).thenReturn(true);
    when(git.remoteBranchExists(root.resolve("web-repo"), "feature/parent")).thenReturn(false);

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("ABC-3", "api")
                    .alsoIn(List.of("web"))
                    .baseBranch("feature/parent")
                    .build(),
                apiWeb,
                BranchStrategy.FRESH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not exist on web's origin");

    verify(git, never()).createWorktree(any(), any(), any(), any(), any());
  }

  @Test
  void findsTheProjectHoldingBranchOfTheTaskAmongEveryConfiguredProjectWhenNoneIsNamed() {
    when(config.load()).thenReturn(apiWebDev);
    when(git.branchExists(Path.of("/web"), "ABC-1")).thenReturn(true);

    assertThat(worktrees.existingBranchProject("ABC-1", null)).isEqualTo("web");
  }

  @Test
  void asksOnlyTheProjectsItWasGivenWhereTheBranchLivesInAnother() {
    when(config.load()).thenReturn(apiWebDev);
    when(git.branchExists(Path.of("/web"), "ABC-1")).thenReturn(true);

    assertThat(worktrees.existingBranchProject("ABC-1", List.of("api"))).isNull();
  }

  @Test
  void stepsOverNineteenTakenNamesToTheTwentiethBeforeGivingUp() throws Exception {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    Files.writeString(
        root.resolve("state.json"),
        """
        {"tasks":{"mailer":{},"mailer-2":{},"mailer-3":{},"mailer-4":{},"mailer-5":{},"mailer-6":{},
        "mailer-7":{},"mailer-8":{},"mailer-9":{},"mailer-10":{},"mailer-11":{},"mailer-12":{},
        "mailer-13":{},"mailer-14":{},"mailer-15":{},"mailer-16":{},"mailer-17":{},"mailer-18":{},
        "mailer-19":{}}}\
        """);

    assertThat(worktrees.freeTaskName("mailer", List.of("proj"))).isEqualTo("mailer-20");
  }

  @Test
  void refusesToStepOverTwentyTakenNames() throws Exception {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    Files.writeString(
        root.resolve("state.json"),
        """
        {"tasks":{"mailer":{},"mailer-2":{},"mailer-3":{},"mailer-4":{},"mailer-5":{},"mailer-6":{},
        "mailer-7":{},"mailer-8":{},"mailer-9":{},"mailer-10":{},"mailer-11":{},"mailer-12":{},
        "mailer-13":{},"mailer-14":{},"mailer-15":{},"mailer-16":{},"mailer-17":{},"mailer-18":{},
        "mailer-19":{},"mailer-20":{}}}\
        """);

    assertThatThrownBy(() -> worktrees.freeTaskName("mailer", List.of("proj")))
        .isInstanceOf(Refusal.class)
        .hasMessage("20 tasks are already called 'mailer' — open the line with different words");
  }

  @Test
  void refusesProjectKeyThatIsNotPlainBeforeTouchingGit() {
    ConfigService.ConfigFile configured =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                Map.of(
                    "bad key",
                    new ProjectConfig(
                        root.resolve("repo").toString(), "origin/main", null, List.of())));

    assertThatThrownBy(() ->
            worktrees.cut(
                NewTask.builder("ABC-42", "bad key").build(), configured, BranchStrategy.FRESH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Argument 'projectKey' must match");
    verifyNoInteractions(git);
  }

  @Test
  void trustsWhatGitObeysAroundTheWorktreesJustCut() {
    when(git.gitState(List.of(Path.of("/ABC-1-api"))))
        .thenReturn(Map.of("core.editor in /api/.git/config", "a1"));

    assertThat(worktrees.trust(List.of(apiRepo)))
        .isEqualTo(GitTrust.of(Map.of("core.editor in /api/.git/config", "a1")));
  }

  @Test
  void trustsNothingWhereGitStateCannotBeRead() {
    when(git.gitState(any())).thenThrow(new IllegalStateException("git rev-parse failed"));

    assertThat(worktrees.trust(List.of(apiRepo))).isNull();
  }

  @Test
  void removesTheBranchItCreatedAndLeavesTheRepositoryAloneWhenRegistrationFailedAfterTheCut() {

    worktrees.uncut(
        NewTask.builder("ABC-1", "api").build(), List.of(apiRepo), BranchStrategy.FRESH);

    verify(git).removeWorktree(Path.of("/api"), Path.of("/ABC-1-api"), "ABC-1");
    verify(git, never()).reattach(any(), any());
  }

  @Test
  void keepsTheResumedBranchAndGivesTheRepositoryBackWhenRegistrationFailedAfterTheCut() {

    worktrees.uncut(
        NewTask.builder("ABC-1", "api").build(), List.of(apiRepo), BranchStrategy.RESUME);

    verify(git).removeWorktree(Path.of("/api"), Path.of("/ABC-1-api"), null);
    verify(git).reattach(Path.of("/api"), "ABC-1");
  }

  @Test
  void cutsTheTaskBranchInTheRequestedProjectFromThatProjectsOwnBase() {
    when(config.load()).thenReturn(registryRelease);
    state.putTask("ABC-7", attaching);

    worktrees.attach("ABC-7", "registry", registryRelease);

    verify(git)
        .createWorktree(
            root.resolve("registry-repo"),
            root.resolve("data/worktrees/registry/ABC-7"),
            "ABC-7",
            "origin/release",
            BranchStrategy.FRESH);
  }

  @Test
  void registersTheAttachedRepositoryAfterTheSessionsOwnAndEndsTheRequest() {
    when(config.load()).thenReturn(registryMain);
    state.putTask("ABC-7", attaching);

    worktrees.attach("ABC-7", "registry", registryMain);

    TaskState task = state.task("ABC-7").orElseThrow();
    assertThat(task.projects()).containsExactly("api", "registry");
    assertThat(task.repoRequest()).isNull();
  }

  @Test
  void writesEveryWorktreeAgainForTheSetThatNowIncludesTheNewOne() {
    when(config.load()).thenReturn(registryMain);
    state.putTask("ABC-7", attaching);

    NewRepo added = worktrees.attach("ABC-7", "registry", registryMain);

    verify(setup)
        .attach(
            any(),
            eq(added),
            argThat(all ->
                all.stream().map(NewRepo::project).toList().equals(List.of("api", "registry"))));
  }

  @Test
  void leavesNothingOfTheNewWorktreeAndKeepsTheRequestWhenWritingItFails() {
    when(config.load()).thenReturn(registryMain);
    state.putTask("ABC-7", attaching);
    doThrow(new IllegalStateException("provisioning failed"))
        .when(setup)
        .attach(any(), any(), any());

    assertThatThrownBy(() -> worktrees.attach("ABC-7", "registry", registryMain))
        .isInstanceOf(IllegalStateException.class);

    verify(git)
        .removeWorktree(
            root.resolve("registry-repo"), root.resolve("data/worktrees/registry/ABC-7"), "ABC-7");
    TaskState task = state.task("ABC-7").orElseThrow();
    assertThat(task.projects()).containsExactly("api");
    assertThat(task.repoRequest()).isEqualTo("registry");
  }

  @Test
  void adoptsWorktreeAlreadyStandingOnTheTaskBranchInsteadOfCuttingOne() {
    when(config.load()).thenReturn(registryRelease);
    when(git.worktreeOf(root.resolve("registry-repo"), "ABC-7"))
        .thenReturn(Optional.of(root.resolve("cut-by-hand")));
    state.putTask("ABC-7", attaching);

    worktrees.attach("ABC-7", "registry", registryRelease);

    verify(git, never()).createWorktree(any(), any(), any(), any(), any());
    assertThat(state.task("ABC-7").orElseThrow().repos())
        .extracting(TaskRepo::worktreePath)
        .containsExactly(
            root.resolve("data/worktrees/api/ABC-7").toString(),
            root.resolve("cut-by-hand").toString());
  }

  @Test
  void runsNoSetupInWorktreeStandingOnTheTaskBranchThatJagtDidNotCut() {
    when(config.load()).thenReturn(registryRelease);
    when(git.worktreeOf(root.resolve("registry-repo"), "ABC-7"))
        .thenReturn(Optional.of(root.resolve("cut-by-hand")));
    state.putTask("ABC-7", attaching);

    worktrees.attach("ABC-7", "registry", registryRelease);

    verify(setup, never()).setUp(any(), any());
  }

  @Test
  void runsTheSetupInTheWorktreeItCutForTheAddedRepository() {
    when(config.load()).thenReturn(registryMain);
    state.putTask("ABC-7", attaching);

    NewRepo added = worktrees.attach("ABC-7", "registry", registryMain);

    verify(setup).setUp(any(), eq(added));
  }

  @Test
  void leavesAnAdoptedWorktreeAndItsWorkInPlaceWhenWritingItFails() {
    when(config.load()).thenReturn(registryRelease);
    when(git.worktreeOf(root.resolve("registry-repo"), "ABC-7"))
        .thenReturn(Optional.of(root.resolve("cut-by-hand")));
    state.putTask("ABC-7", attaching);
    doThrow(new IllegalStateException("provisioning failed"))
        .when(setup)
        .attach(any(), any(), any());

    assertThatThrownBy(() -> worktrees.attach("ABC-7", "registry", registryRelease))
        .isInstanceOf(IllegalStateException.class);

    verify(git, never()).removeWorktree(any(), any(), any());
  }

  @Test
  void trustsTheAddedRepositorysGitStateAsItStandsAndKeepsTheRecordOfTheOthers() {
    when(config.load()).thenReturn(registryMain);
    when(git.gitState(List.of(root.resolve("data/worktrees/registry/ABC-7"))))
        .thenReturn(Map.of("core.bare in registry", "d2"));
    state.putTask(
        "ABC-7",
        TaskState.builder(
                "api", root.resolve("data/worktrees/api/ABC-7").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repoRequest("registry")
            .gitTrust(GitTrust.of(Map.of("core.bare in api", "d1")))
            .build());

    worktrees.attach("ABC-7", "registry", registryMain);

    assertThat(Objects.requireNonNull(state.task("ABC-7").orElseThrow().gitTrust()).recorded())
        .isEqualTo(Map.of("core.bare in api", "d1", "core.bare in registry", "d2"));
  }

  @Test
  void refusesProjectTheTaskAlreadyWorksInBeforeCuttingAnything() {
    ConfigService.ConfigFile configured =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                Map.of(
                    "api",
                    new ProjectConfig(
                        root.resolve("api-repo").toString(), "origin/main", null, List.of())));
    when(config.load()).thenReturn(configured);
    state.putTask(
        "ABC-7",
        TaskState.builder(
                "api", root.resolve("data/worktrees/api/ABC-7").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repoRequest("api")
            .build());

    assertThatThrownBy(() -> worktrees.attach("ABC-7", "api", configured))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));

    verify(git, never()).createWorktree(any(), any(), any(), any(), any());
  }

  @Test
  void handsBackTheScratchFolderOfTaskThatPlansBeforeItHasRepository() {
    NewTask request = NewTask.unplacedBuilder("ABC-9").build();
    when(setup.scratch(request)).thenReturn(root.resolve("scratch/ABC-9"));

    Path folder = worktrees.scratch(request, ConfigService.ConfigFile.defaults());

    assertThat(folder).isEqualTo(root.resolve("scratch/ABC-9"));
  }

  @Test
  void refusesScratchFolderForTaskNameAnotherTaskHolds() {
    state.putTask("ABC-9", TaskState.builder("proj", "/first", TaskStatus.IN_PROGRESS).build());

    assertThatThrownBy(() ->
            worktrees.scratch(
                NewTask.unplacedBuilder("ABC-9").build(), ConfigService.ConfigFile.defaults()))
        .isInstanceOf(Refusal.class);
    verifyNoInteractions(setup);
  }

  @Test
  void cutsWorktreePerRepositoryTheFirstBeingThePrimary() {
    when(config.load()).thenReturn(apiWebDev);

    List<NewRepo> placed =
        worktrees.place(
            NewTask.builder("ABC-9", "api").alsoIn(List.of("web")).build(), apiWebDev, repos -> {});

    assertThat(placed)
        .extracting(NewRepo::project, NewRepo::primary)
        .containsExactly(tuple("api", true), tuple("web", false));
    verify(git)
        .createWorktree(
            Path.of("/api"),
            root.resolve("data/worktrees/api/ABC-9"),
            "ABC-9",
            "origin/main",
            BranchStrategy.FRESH);
    verify(git)
        .createWorktree(
            Path.of("/web"),
            root.resolve("data/worktrees/web/ABC-9"),
            "ABC-9",
            "origin/main",
            BranchStrategy.FRESH);
    verify(setup, times(2)).fill(any(), any(), any());
  }

  @Test
  void resumesBranchHoldingOwnCommitsAndRecreatesLeftoverOneWhenPlacing() {
    when(config.load()).thenReturn(apiWebDev);
    when(git.branchExists(Path.of("/api"), "ABC-9")).thenReturn(true);
    when(git.holdsOwnCommits(Path.of("/api"), "ABC-9", "origin/main")).thenReturn(true);
    when(git.branchExists(Path.of("/web"), "ABC-9")).thenReturn(true);

    worktrees.place(
        NewTask.builder("ABC-9", "api").alsoIn(List.of("web")).build(), apiWebDev, repos -> {});

    verify(git)
        .createWorktree(
            Path.of("/api"),
            root.resolve("data/worktrees/api/ABC-9"),
            "ABC-9",
            "origin/main",
            BranchStrategy.RESUME);
    verify(git)
        .createWorktree(
            Path.of("/web"),
            root.resolve("data/worktrees/web/ABC-9"),
            "ABC-9",
            "origin/main",
            BranchStrategy.RECREATE);
  }

  @Test
  void runsTheSetupOfEveryWorktreeItCutsWhenPlacing() {
    when(config.load()).thenReturn(apiWebDev);

    worktrees.place(
        NewTask.builder("ABC-9", "api").alsoIn(List.of("web")).build(), apiWebDev, repos -> {});

    verify(setup, times(2)).setUp(any(), any());
  }

  @Test
  void runsTheSetupOfEveryWorktreeCutBeforeTheTaskStarts() {
    List<NewRepo> cut =
        worktrees.cut(
            NewTask.builder("ABC-8", "api").alsoIn(List.of("web")).build(),
            apiWeb,
            BranchStrategy.FRESH);

    worktrees.setUp(NewTask.builder("ABC-8", "api").alsoIn(List.of("web")).build(), cut);

    verify(setup, times(2)).setUp(any(), any());
  }

  @Test
  void cutsNothingWhereProjectIsNotConfigured() {
    ConfigService.ConfigFile configured =
        ConfigService.ConfigFile.defaults()
            .withProjects(
                Map.of("api", new ProjectConfig("/api", "origin/main", "dev", List.of())));
    when(config.load()).thenReturn(configured);

    assertThatThrownBy(() ->
            worktrees.place(
                NewTask.builder("ABC-9", "api").alsoIn(List.of("billing")).build(),
                configured,
                repos -> {}))
        .isInstanceOfSatisfying(
            Unplaceable.class,
            unplaceable -> assertThat(unplaceable.projects()).containsExactly("billing"));

    verify(git, never()).createWorktree(any(), any(), any(), any(), any());
  }

  @Test
  void cutsNothingWhereTypedBaseBranchIsNotOnProjectsOrigin() {
    when(config.load()).thenReturn(apiWebDev);
    when(git.remoteBranchExists(Path.of("/api"), "feature/parent")).thenReturn(true);

    assertThatThrownBy(() ->
            worktrees.place(
                NewTask.builder("ABC-9", "api")
                    .alsoIn(List.of("web"))
                    .baseBranch("feature/parent")
                    .build(),
                apiWebDev,
                repos -> {}))
        .isInstanceOfSatisfying(
            Unplaceable.class,
            unplaceable -> assertThat(unplaceable.projects()).containsExactly("web"));

    verify(git, never()).createWorktree(any(), any(), any(), any(), any());
  }

  @Test
  void removesTheFirstWorktreeWhereTheSecondCutFailsWhenPlacing() {
    when(config.load()).thenReturn(apiWebDev);
    doThrow(new IllegalStateException("git failed"))
        .when(git)
        .createWorktree(
            Path.of("/web"),
            root.resolve("data/worktrees/web/ABC-9"),
            "ABC-9",
            "origin/main",
            BranchStrategy.FRESH);

    assertThatThrownBy(() ->
            worktrees.place(
                NewTask.builder("ABC-9", "api").alsoIn(List.of("web")).build(),
                apiWebDev,
                repos -> {}))
        .isInstanceOf(IllegalStateException.class);

    verify(git).removeWorktree(Path.of("/api"), root.resolve("data/worktrees/api/ABC-9"), "ABC-9");
    verify(git, never()).removeWorktree(eq(Path.of("/web")), any(), any());
  }

  @Test
  void removesEveryWorktreeWhereTheRegistrationFailsWhenPlacing() {
    when(config.load()).thenReturn(apiWebDev);

    assertThatThrownBy(() ->
            worktrees.place(
                NewTask.builder("ABC-9", "api").alsoIn(List.of("web")).build(),
                apiWebDev,
                repos -> {
                  throw new IllegalStateException("state not written");
                }))
        .isInstanceOf(IllegalStateException.class);

    verify(git).removeWorktree(Path.of("/api"), root.resolve("data/worktrees/api/ABC-9"), "ABC-9");
    verify(git).removeWorktree(Path.of("/web"), root.resolve("data/worktrees/web/ABC-9"), "ABC-9");
  }
}
