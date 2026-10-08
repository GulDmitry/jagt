package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NewTaskWorktreesTest {

    @TempDir
    Path root;

    private final ConfigService config = mock(ConfigService.class);
    private final GitWorktrees git = mock(GitWorktrees.class);
    private final WorktreeSetup setup = mock(WorktreeSetup.class);
    private StateService state;
    private NewTaskWorktrees worktrees;

    @BeforeEach
    void setUp() {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString());
        state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        worktrees = new NewTaskWorktrees(config, state, git, setup);
    }

    @Test
    void stepsOverAWrittenNameALiveTaskAlreadyTookRatherThanRefusingIt() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        state.putTask("split-the-mailer", TaskState.builder("proj", "/first", TaskStatus.IN_PROGRESS)
                .alias("a1").build());

        assertThat(worktrees.freeTaskName("split-the-mailer", List.of("proj"))).isEqualTo("split-the-mailer-2");
    }

    @ParameterizedTest
    @CsvSource({"false, false, FRESH", "true, false, RECREATE", "true, true, RESUME"})
    void intakeRecreatesOnlyALeftoverBranchHoldingNoWorkOfItsOwn(boolean exists, boolean holdsWork,
                                                                 BranchStrategy expected) {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of()))));
        when(git.branchExists(Path.of("/api"), "ABC-1")).thenReturn(exists);
        when(git.holdsOwnCommits(Path.of("/api"), "ABC-1", "origin/main")).thenReturn(holdsWork);

        assertThat(worktrees.strategyForExisting("ABC-1", "api")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape", "a b", "-lead", "feature/", "feature//x", "x.lock"})
    void rejectsTaskIdBeforeTouchingGitWhenGitWouldRefuseItAsABranch(String unsafeTaskId) {
        assertThatThrownBy(() -> worktrees.cut(NewTask.builder(unsafeTaskId, "proj").build(),
                ConfigService.ConfigFile.defaults(), BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a branch name");
        verifyNoInteractions(git);
    }

    @ParameterizedTest
    @CsvSource({"main,", "Main,", "origin/main,", "dev,", "release,release"})
    void refusesATaskWhoseBranchIsASharedOneBeforeTouchingGit(String taskId, String baseBranch) {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(root.resolve("repo").toString(), "origin/main", "refs/heads/dev", List.of())));

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder(taskId, "proj").baseBranch(baseBranch).build(),
                configured, BranchStrategy.RESUME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("would be the shared branch");
        verifyNoInteractions(git);
    }

    @Test
    void refusesABaseBranchGitWouldReadAsAnOptionBeforeTouchingGit() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(root.resolve("repo").toString(), "origin/main", null, List.of())));

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder("ABC-42", "proj").baseBranch("--upload-pack=touch")
                .build(), configured, BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a branch name: it starts with '-'");
        verifyNoInteractions(git);
    }

    @Test
    void cutsOneFlatWorktreeForASlashedBranchTakenOverFromSomeoneElse() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(root.resolve("repo").toString(), "origin/main", null, List.of())));

        worktrees.cut(NewTask.builder("feature/ABC-42", "proj").build(), configured, BranchStrategy.FRESH);

        verify(git).createWorktree(root.resolve("repo"), root.resolve("feature-ABC-42-proj"),
                "feature/ABC-42", "origin/main", BranchStrategy.FRESH);
    }

    @Test
    void refusesASecondTaskWhoseBranchBecomesTheDirectoryOfALiveOne() {
        state.putTask("feature/ABC-42", TaskState.builder("proj", "/first", TaskStatus.IN_PROGRESS)
                .alias("f1").build());

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder("feature-ABC-42", "proj").build(),
                ConfigService.ConfigFile.defaults(), BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both become the directory feature-ABC-42");
    }

    @Test
    void refusesTheTwentyFifthTaskAsTheStateNotTheCall() throws Exception {
        Files.writeString(root.resolve("state.json"), """
                {"tasks":{"T01":{},"T02":{},"T03":{},"T04":{},"T05":{},"T06":{},"T07":{},"T08":{},
                "T09":{},"T10":{},"T11":{},"T12":{},"T13":{},"T14":{},"T15":{},"T16":{},
                "T17":{},"T18":{},"T19":{},"T20":{},"T21":{},"T22":{},"T23":{},"T24":{}}}""");

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder("ABC-99", "proj").build(),
                ConfigService.ConfigFile.defaults(), BranchStrategy.FRESH))
                .isInstanceOfSatisfying(Refusal.class, refusal -> assertThat(refusal.code())
                        .isEqualTo(Refusal.Code.STATE))
                .hasMessageContaining("24 tasks are already open");
    }

    @Test
    void cutsTheTwentyFourthTask() throws Exception {
        Files.writeString(root.resolve("state.json"), """
                {"tasks":{"T01":{},"T02":{},"T03":{},"T04":{},"T05":{},"T06":{},"T07":{},"T08":{},
                "T09":{},"T10":{},"T11":{},"T12":{},"T13":{},"T14":{},"T15":{},"T16":{},
                "T17":{},"T18":{},"T19":{},"T20":{},"T21":{},"T22":{},"T23":{}}}""");
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(root.resolve("repo").toString(), "origin/main", null, List.of())));

        worktrees.cut(NewTask.builder("ABC-99", "proj").build(), configured, BranchStrategy.FRESH);

        verify(git).createWorktree(root.resolve("repo"), root.resolve("ABC-99-proj"), "ABC-99", "origin/main",
                BranchStrategy.FRESH);
    }

    @Test
    void removesFreshWorktreeAndBranchWhenContextSetupFailsAfterCheckout() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(root.resolve("repo").toString(), "origin/main", null, List.of())));
        doThrow(new IllegalStateException("provisioning failed")).when(setup).fill(any(), any(), any());

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder("ABC-9", "proj").build(), configured,
                BranchStrategy.FRESH))
                .isInstanceOf(IllegalStateException.class);

        verify(git).removeWorktree(root.resolve("repo"), root.resolve("ABC-9-proj"), "ABC-9");
    }

    @Test
    void keepsAResumedBranchWhenTheUnwindRemovesItsWorktree() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(root.resolve("repo").toString(), "origin/main", null, List.of())));
        doThrow(new IllegalStateException("provisioning failed")).when(setup).fill(any(), any(), any());

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder("ABC-4", "proj").build(), configured,
                BranchStrategy.RESUME))
                .isInstanceOf(IllegalStateException.class);

        verify(git).removeWorktree(root.resolve("repo"), root.resolve("ABC-4-proj"), null);
    }

    @Test
    void cutsOneWorktreePerProjectFromEachProjectsOwnBase() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(new LinkedHashMap<>(
                Map.of("api", new ProjectConfig(root.resolve("api-repo").toString(), "origin/main", null, List.of()),
                        "web", new ProjectConfig(root.resolve("web-repo").toString(), "origin/release", null,
                                List.of()))));

        List<NewRepo> repos = worktrees.cut(NewTask.builder("ABC-7", "api").alsoIn(List.of("web")).build(),
                configured, BranchStrategy.FRESH);

        assertThat(repos).extracting(NewRepo::project).containsExactly("api", "web");
        verify(git).createWorktree(root.resolve("api-repo"), root.resolve("ABC-7-api"), "ABC-7",
                "origin/main", BranchStrategy.FRESH);
        verify(git).createWorktree(root.resolve("web-repo"), root.resolve("ABC-7-web"), "ABC-7",
                "origin/release", BranchStrategy.FRESH);
    }

    @Test
    void unwindsTheWorktreesItAlreadyCutWhenALaterRepositoryFails() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(new LinkedHashMap<>(
                Map.of("api", new ProjectConfig(root.resolve("api-repo").toString(), "origin/main", null, List.of()),
                        "web", new ProjectConfig(root.resolve("web-repo").toString(), "origin/release", null,
                                List.of()))));
        doNothing().doThrow(new IllegalStateException("second repo failed")).when(setup).fill(any(), any(), any());

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder("ABC-8", "api").alsoIn(List.of("web")).build(),
                configured, BranchStrategy.FRESH))
                .isInstanceOf(IllegalStateException.class);

        verify(git).removeWorktree(root.resolve("api-repo"), root.resolve("ABC-8-api"), "ABC-8");
        verify(git).removeWorktree(root.resolve("web-repo"), root.resolve("ABC-8-web"), "ABC-8");
    }

    @Test
    void refusesTheWholeTaskWhenOneOfItsProjectsIsUnknown() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(Map.of("api",
                new ProjectConfig(root.resolve("api-repo").toString(), "origin/main", null, List.of())));

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder("ABC-6", "api").alsoIn(List.of("nope")).build(),
                configured, BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown project 'nope'");

        verify(git, never()).createWorktree(any(), any(), any(), any(), any());
    }

    @Test
    void cutsTheWorktreeFromTheBranchTheHumanNamed() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(root.resolve("repo").toString(), "origin/main", null, List.of())));
        when(git.remoteBranchExists(any(), eq("feature/parent"))).thenReturn(true);

        worktrees.cut(NewTask.builder("ABC-3", "proj").baseBranch("origin/feature/parent").build(), configured,
                BranchStrategy.FRESH);

        verify(git).createWorktree(root.resolve("repo"), root.resolve("ABC-3-proj"), "ABC-3", "feature/parent",
                BranchStrategy.FRESH);
    }

    @Test
    void refusesABaseBranchOriginDoesNotHaveBeforeCreatingAnything() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(root.resolve("repo").toString(), "origin/main", null, List.of())));
        when(git.remoteBranchExists(any(), eq("feature/typo"))).thenReturn(false);

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder("ABC-5", "proj").baseBranch("feature/typo").build(),
                configured, BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist on proj's origin");

        verify(git, never()).createWorktree(any(), any(), any(), any(), any());
    }

    @Test
    void namesTheRepositoryWhoseOriginIsMissingTheBaseBranch() {
        ConfigService.ConfigFile configured = ConfigService.ConfigFile.defaults().withProjects(new LinkedHashMap<>(
                Map.of("api", new ProjectConfig(root.resolve("api-repo").toString(), "origin/main", null, List.of()),
                        "web", new ProjectConfig(root.resolve("web-repo").toString(), "origin/release", null,
                                List.of()))));
        when(git.remoteBranchExists(root.resolve("api-repo"), "feature/parent")).thenReturn(true);
        when(git.remoteBranchExists(root.resolve("web-repo"), "feature/parent")).thenReturn(false);

        assertThatThrownBy(() -> worktrees.cut(NewTask.builder("ABC-3", "api").alsoIn(List.of("web"))
                .baseBranch("feature/parent").build(), configured, BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist on web's origin");

        verify(git, never()).createWorktree(any(), any(), any(), any(), any());
    }
}
