package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskRepo;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.EditorDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one path that writes a SHARED branch. Every test is about what it refuses, or about the record it leaves
 * so the undo has an exact commit to revert.
 */
class DeployServiceTest {

    private final EditorDriver editor = mock(EditorDriver.class);

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void movesTaskToDeployedAfterASuccessfulDeploy(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        DeployService deploys = new DeployService(state, config, mock(GitService.class), editor);

        deploys.deploy("a1");

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    /** Without the commit stored, `revert` has nothing exact to undo and can only send the human to git. */
    @Test
    void recordsTheMergeCommitTheDeployCreatedSoItCanBeReverted(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");
        DeployService deploys = new DeployService(state, config, git, editor);

        String result = deploys.deploy("a1");

        assertThat(state.task("ABC-1").orElseThrow().deployCommit()).isEqualTo("cafebabe1234");
        assertThat(result).contains("cafebabe");
    }

    @Test
    void revertsTheDeployedMergeAndMovesTheTaskToReverted(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .deployCommit("cafebabe1234").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234")))
                .thenReturn("f00dfeed5678");
        DeployService deploys = new DeployService(state, config, git, editor);

        String result = deploys.revert("a1");

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVERTED);
        assertThat(result).contains("f00dfeed");
    }

    /**
     * A task deployed before jagt stored the commit: the message must be the by-hand recipe, because guessing
     * which merge to revert on a SHARED branch is the one mistake here with no cheap undo.
     */
    @Test
    void refusesToGuessTheMergeCommitOfADeployItDidNotRecord(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        DeployService deploys = new DeployService(state, config, git, editor);

        assertThatThrownBy(() -> deploys.revert("a1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("git revert -m 1");
        verify(git, never()).revertMergeAndPush(any(), anyString(), anyString(), anyString());
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void sendsTheHumanToEveryRepositoryWhenNoMergeCommitWasEverRecorded(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "staging", null));
        DeployService deploys = new DeployService(state, config, mock(GitService.class), editor);

        assertThatThrownBy(() -> deploys.revert("a1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("origin/dev` in api")
                .hasMessageContaining("origin/staging` in web");
    }

    @Test
    void refusesToRevertATaskThatWasNeverDeployed(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        GitService git = mock(GitService.class);
        DeployService deploys = new DeployService(state, mock(ConfigService.class), git, editor);

        assertThatThrownBy(() -> deploys.revert("a1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only a DEPLOYED one");
        verifyNoInteractions(git);
    }

    @Test
    void flagsDeployConflictOnTheDashboardWithoutOpeningAnEditorOrTouchingTheTaskBranch(@TempDir Path root)
            throws Exception {
        StateService state = stateIn(root);
        Path worktree = java.nio.file.Files.createDirectories(root.resolve("wt"));
        state.putTask("ABC-1", TaskState.builder("proj", worktree.toString(), TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        GitService git = mock(GitService.class);
        Path deployWorktree = root.resolve("ABC-1-deploy");
        doThrow(new GitService.MergeConflictException("ABC-1", "dev", "conflict in liquibase/master.yaml", deployWorktree))
                .when(git).mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"));
        DeployService deploys = new DeployService(state, config, git, editor);

        String result = deploys.deploy("a1");

        // The human resolves it himself — jagt opens no editor, briefs no agent, and never touches the task branch.
        verifyNoInteractions(editor);
        assertThat(worktree.resolve("task_context.md")).doesNotExist();
        assertThat(result).contains(deployWorktree.toString()).contains("nothing pushed").contains("deploy ABC-1");
        TaskState after = state.task("ABC-1").orElseThrow();
        assertThat(after.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
        assertThat(after.message()).contains(deployWorktree.toString());
    }

    @Test
    void refusesDeployWhenDeployBranchIsTheBaseBranch(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/release", "release", null));
        GitService git = mock(GitService.class);
        DeployService deploys = new DeployService(state, config, git, editor);

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base branch");
        verifyNoInteractions(git);
    }

    @Test
    void refusesDeployWhenProjectHasNoDeployBranch(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", null, null));
        DeployService deploys = new DeployService(state, config, mock(GitService.class), editor);

        assertThatThrownBy(() -> deploys.deploy("ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deployBranch");
    }
    @Test
    void landsEveryRepositoryATaskSpansInTheOrderItHoldsThem(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
        DeployService deploys = new DeployService(state, config, git, editor);

        String result = deploys.deploy("a1");

        assertThat(result).contains("api into dev (cafebabe)", "web into dev (f00dfeed)", "DEPLOYED");
        TaskState after = state.task("ABC-1").orElseThrow();
        assertThat(after.status()).isEqualTo(TaskStatus.DEPLOYED);
        assertThat(after.repos()).extracting(TaskRepo::deployCommit)
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
        GitService git = mock(GitService.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        Path deployWorktree = root.resolve("ABC-1-deploy");
        doThrow(new GitService.MergeConflictException("ABC-1", "dev", "conflict in Widget.java", deployWorktree))
                .when(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
        DeployService deploys = new DeployService(state, config, git, editor);

        String result = deploys.deploy("a1");

        assertThat(result).contains("CONFLICT merging web into dev", "Live on the deploy branch: api",
                "NOT deployed: web", deployWorktree.toString());
        TaskState after = state.task("ABC-1").orElseThrow();
        assertThat(after.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
        assertThat(after.repos()).extracting(TaskRepo::deployCommit).containsExactly("cafebabe1234", null);
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
        GitService git = mock(GitService.class);
        doThrow(new GitService.MergeConflictException("ABC-1", "dev", "conflict", root.resolve("ABC-1-deploy")))
                .when(git).mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");
        DeployService deploys = new DeployService(state, config, git, editor);

        String result = deploys.deploy("a1");

        assertThat(result).contains("Live on the deploy branch: none", "NOT deployed: api, web");
    }

    @Test
    void startsFromTheTopWhenADeployWorktreeIsLeftOverFromSomeRoundOtherThanAConflict(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");
        DeployService deploys = new DeployService(state, config, git, editor);

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
        GitService git = mock(GitService.class);
        when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);
        when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
        DeployService deploys = new DeployService(state, config, git, editor);

        deploys.deploy("a1");

        verify(git, never()).mergeIntoAndPush(eq(Path.of("/repo/api")), anyString(), anyString());
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void landsTheRepositoriesThatHaveWorkAndPassesOverTheOnesWithNothingToDeploy(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        doThrow(new GitService.NothingToDeployException("ABC-1", "dev"))
                .when(git).mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");
        when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
        DeployService deploys = new DeployService(state, config, git, editor);

        String result = deploys.deploy("a1");

        assertThat(result).contains("web into dev (f00dfeed)", "nothing to deploy in api", "DEPLOYED");
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void refusesTheDeployWhenNoRepositoryHasAnythingToDeploy(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        doThrow(new GitService.NothingToDeployException("ABC-1", "dev"))
                .when(git).mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"));
        DeployService deploys = new DeployService(state, config, git, editor);

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(GitService.NothingToDeployException.class)
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
        GitService git = mock(GitService.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        doThrow(new IllegalStateException("Deploy push to dev was rejected"))
                .when(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
        DeployService deploys = new DeployService(state, config, git, editor);

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Live on the deploy branch: api")
                .hasMessageContaining("NOT deployed: web")
                .hasMessageContaining("Deploy push to dev was rejected");
        TaskState after = state.task("ABC-1").orElseThrow();
        assertThat(after.status()).isEqualTo(TaskStatus.APPROVED);
        assertThat(after.message()).contains("Live on the deploy branch: api", "NOT deployed: web");
    }

    @Test
    void leavesNoDanglingWordWhenTheFailureItReportsCarriesNoMessage(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        doThrow(new NullPointerException()).when(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
        DeployService deploys = new DeployService(state, config, git, editor);

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("NOT deployed: web.");
    }

    @Test
    void undoesTheRepositoryThatIsLiveWithoutEvenLookingUpOneThatNeverLanded(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234"))
                .thenReturn("beef00991122");
        DeployService deploys = new DeployService(state, config, git, editor);

        String result = deploys.revert("a1");

        assertThat(result).contains("reverted api on dev (beef0099)", "REVERTED");
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVERTED);
    }

    @Test
    void refusesTheWholeDeployWhenAnyRepositoryHasNoDeployBranch(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", null, null));
        GitService git = mock(GitService.class);
        DeployService deploys = new DeployService(state, config, git, editor);

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("web")
                .hasMessageContaining("deployBranch");
        verifyNoInteractions(git);
    }

    @Test
    void recordsWhatCameOutWhenARevertStopsPartWaySoTheBoardStillSaysIt(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234")
                .withDeployCommit("web", "f00dfeed5678"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.revertMergeAndPush(Path.of("/repo/web"), "ABC-1", "dev", "f00dfeed5678"))
                .thenReturn("beef00991122");
        doThrow(new IllegalStateException("the revert conflicts"))
                .when(git).revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234");
        DeployService deploys = new DeployService(state, config, git, editor);

        assertThatThrownBy(() -> deploys.revert("a1")).isInstanceOf(IllegalStateException.class);

        assertThat(state.task("ABC-1").orElseThrow().message())
                .contains("reverted web on dev", "api still live on dev");
    }

    @Test
    void undoesTheRepositoriesInReverseOrderAndForgetsEachMergeItTookOut(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234")
                .withDeployCommit("web", "f00dfeed5678"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.revertMergeAndPush(any(), anyString(), anyString(), anyString())).thenReturn("beef00991122");
        DeployService deploys = new DeployService(state, config, git, editor);

        String result = deploys.revert("a1");

        InOrder undone = inOrder(git);
        undone.verify(git).revertMergeAndPush(Path.of("/repo/web"), "ABC-1", "dev", "f00dfeed5678");
        undone.verify(git).revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234");
        assertThat(result).contains("reverted web on dev", "api on dev", "REVERTED");
        TaskState after = state.task("ABC-1").orElseThrow();
        assertThat(after.status()).isEqualTo(TaskStatus.REVERTED);
        assertThat(after.repos()).extracting(TaskRepo::deployCommit).containsOnlyNulls();
    }

    @Test
    void keepsATaskDeployedWhenOneRepositoryCouldNotBeUndone(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234")
                .withDeployCommit("web", "f00dfeed5678"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.revertMergeAndPush(Path.of("/repo/web"), "ABC-1", "dev", "f00dfeed5678"))
                .thenReturn("beef00991122");
        doThrow(new IllegalStateException("the revert conflicts with work done there since the deploy"))
                .when(git).revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234");
        DeployService deploys = new DeployService(state, config, git, editor);

        assertThatThrownBy(() -> deploys.revert("a1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reverted web on dev")
                .hasMessageContaining("api still live on dev");
        TaskState after = state.task("ABC-1").orElseThrow();
        assertThat(after.status()).isEqualTo(TaskStatus.DEPLOYED);
        assertThat(after.repos()).extracting(TaskRepo::deployCommit).containsExactly("cafebabe1234", null);
    }
}
