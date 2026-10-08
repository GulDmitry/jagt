package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.EditorDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployServiceTest {

    private final EditorDriver editor = mock(EditorDriver.class);

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void reportsAFullDeployAsDoneAndStampsTheBranchItLandedOn(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.stamp()).isEqualTo("deployed to dev");
    }

    @Test
    void aBlockedDeployWithNothingLandedNamesTheObstacleInsteadOfAdvisingTheSameRunAgain(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenThrow(
                new GitDeploy.ForeignDeployWorktreeException(Path.of("/repos/ABC-1-deploy"), Path.of("/repos/x")));
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.message()).contains("/repos/ABC-1-deploy")
                .doesNotContain("Run `deploy ABC-1` again");
    }

    @Test
    void recordsTheMergeCommitTheDeployCreatedSoItCanBeReverted(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        assertThat(state.task("ABC-1").orElseThrow().deployCommit()).isEqualTo("cafebabe1234");
        assertThat(outcome.message()).contains("cafebabe");
    }

    @Test
    void handsBackAConflictWithoutOpeningAnEditorOrTouchingTheTaskBranch(@TempDir Path root)
            throws Exception {
        StateService state = stateIn(root);
        Path worktree = java.nio.file.Files.createDirectories(root.resolve("wt"));
        state.putTask("ABC-1", TaskState.builder("proj", worktree.toString(), TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        GitDeploy git = mock(GitDeploy.class);
        Path deployWorktree = root.resolve("ABC-1-deploy");
        doThrow(new GitDeploy.MergeConflictException("ABC-1", "dev", "conflict in schema.yaml", deployWorktree))
                .when(git).mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"));
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        verifyNoInteractions(editor);
        assertThat(worktree.resolve("task_context.md")).doesNotExist();
        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.CONFLICT);
        assertThat(outcome.message()).contains(deployWorktree.toString())
                .contains("nothing pushed").contains("deploy ABC-1");
        assertThat(outcome.stamp()).contains(deployWorktree.toString());
    }

    @Test
    void refusesToLandATaskOntoTheVeryBranchItWasCutFrom(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/release", "release", null));
        GitDeploy git = mock(GitDeploy.class);
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("base branch")
                .extracting("code").isEqualTo(Refusal.Code.STATE);
        verifyNoInteractions(git);
    }

    @Test
    void refusesToGuessWhereToLandAProjectThatNamesNoDeployBranch(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", null, null));
        GitDeploy git = mock(GitDeploy.class);
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        assertThatThrownBy(() -> deploys.deploy("ABC-1"))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("deployBranch")
                .extracting("code").isEqualTo(Refusal.Code.STATE);
    }

    @Test
    void landsEveryRepositoryATaskSpansInTheOrderItHoldsThem(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.message()).contains("api into dev (cafebabe)", "web into dev (f00dfeed)", "DEPLOYED");
        assertThat(state.task("ABC-1").orElseThrow().repos()).extracting(TaskRepo::deployCommit)
                .containsExactly("cafebabe1234", "f00dfeed5678");
    }

    @Test
    void namesWhatIsLiveAndWhatIsNotWhenARepositoryConflictsAfterAnotherHasLanded(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        Path deployWorktree = root.resolve("ABC-1-deploy");
        doThrow(new GitDeploy.MergeConflictException("ABC-1", "dev", "conflict in Widget.java", deployWorktree))
                .when(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.CONFLICT);
        assertThat(outcome.message()).contains("CONFLICT merging web into dev", "Live on the deploy branch: api",
                "NOT deployed: web", deployWorktree.toString());
        assertThat(outcome.stamp()).contains("Live on the deploy branch: api", "NOT deployed: web");
        assertThat(state.task("ABC-1").orElseThrow().repos()).extracting(TaskRepo::deployCommit)
                .containsExactly("cafebabe1234", null);
    }

    @Test
    void countsNothingAsLiveWhenTheFirstRepositoryOfAFreshRoundConflicts(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.REVIEWED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "0ldc0mm1t111")
                .withDeployCommit("web", "0ldc0mm1t222"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        doThrow(new GitDeploy.MergeConflictException("ABC-1", "dev", "conflict", root.resolve("ABC-1-deploy")))
                .when(git).mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.message()).contains("Live on the deploy branch: none", "NOT deployed: api, web");
    }

    @Test
    void startsFromTheTopWhenADeployWorktreeIsLeftOverFromSomeRoundOtherThanAConflict(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        deploys.deploy("a1");

        verify(git).mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");
        verify(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
    }

    @Test
    void aRepeatedDeployPicksUpAtTheRepositoryTheConflictLeftBehind(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);
        when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        verify(git, never()).mergeIntoAndPush(eq(Path.of("/repo/api")), anyString(), anyString());
        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
    }

    @Test
    void landsTheRepositoriesThatHaveWorkAndPassesOverTheOnesWithNothingToDeploy(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        doThrow(new GitDeploy.NothingToDeployException("ABC-1", "dev"))
                .when(git).mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");
        when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.message()).contains("web into dev (f00dfeed)", "nothing to deploy in api", "DEPLOYED");
    }

    @Test
    void refusesTheDeployWhenNoRepositoryHasAnythingToDeploy(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        doThrow(new GitDeploy.NothingToDeployException("ABC-1", "dev"))
                .when(git).mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"));
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(GitDeploy.NothingToDeployException.class)
                .hasMessageContaining("Nothing to deploy");
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.APPROVED);
    }

    @Test
    void reportsAndRecordsWhatIsLiveWhenADeployBreaksOffForAReasonNoWorktreeCanFix(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        doThrow(new IllegalStateException("Deploy push to dev was rejected"))
                .when(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
        assertThat(outcome.message()).contains("Live on the deploy branch: api", "NOT deployed: web",
                "Deploy push to dev was rejected");
        assertThat(outcome.stamp()).contains("Live on the deploy branch: api", "NOT deployed: web");
        assertThat(outcome.cause()).hasMessage("Deploy push to dev was rejected");
    }

    @Test
    void leavesNoDanglingWordWhenTheFailureItReportsCarriesNoMessage(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        doThrow(new NullPointerException()).when(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
        assertThat(outcome.message()).endsWith("NOT deployed: web.");
    }

    @Test
    void refusesTheWholeDeployWhenAnyRepositoryHasNoDeployBranch(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", null, null));
        GitDeploy git = mock(GitDeploy.class);
        DeployService deploys = new DeployService(state, new DeployTargets(config, git), git, editor);

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("web")
                .hasMessageContaining("deployBranch");
        verifyNoInteractions(git);
    }
}
