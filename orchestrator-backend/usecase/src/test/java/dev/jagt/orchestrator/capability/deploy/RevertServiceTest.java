package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.flow.Refusal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevertServiceTest {

    @Test
    void revertsTheDeployedMergeAndReportsTheUndoAsDone(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .deployCommit("cafebabe1234").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), anyList()))
                .thenReturn("f00dfeed5678");
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        Outcome outcome = service.revert("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.message()).contains("f00dfeed");
        assertThat(outcome.stamp()).isEqualTo("reverted on dev (f00dfeed)");
    }

    @Test
    void revertsTheFixesOfADeployWithItsMergeNewestFirst(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .deployCommit("cafebabe1234").build().withDeployFix("proj", "f1x1").withDeployFix("proj", "f1x2"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), eq(List.of("f1x2", "f1x1"))))
                .thenReturn("f00dfeed5678");
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        Outcome outcome = service.revert("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(state.task("ABC-1").orElseThrow().repos().getFirst().deployTip()).isNull();
        verify(git).revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), eq(List.of("f1x2", "f1x1")));
    }

    @Test
    void refusesToGuessTheMergeCommitOfADeployItDidNotRecord(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("proj", new ProjectConfig("/repo", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        assertThatThrownBy(() -> service.revert("a1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("git revert -m 1")
                .hasMessageContaining("`git log --merges --grep ABC-1 origin/dev` to find")
                .hasMessageNotContaining("` in proj");
        verify(git, never()).revertMergeAndPush(any(), anyString(), anyString(), anyString(), anyList());
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void sendsTheHumanToEveryRepositoryWhenNoMergeCommitWasEverRecorded(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/repo/api", "origin/main", "dev", null),
                "web", new ProjectConfig("/repo/web", "origin/main", "staging", null))));
        GitDeploy git = mock(GitDeploy.class);
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        assertThatThrownBy(() -> service.revert("a1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("origin/dev` in api")
                .hasMessageContaining("origin/staging` in web");
    }

    @Test
    void sendsTheHumanToEveryRepositoryEvenOneWhoseProjectLeftTheConfiguration(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("gone", "/gone-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("api", new ProjectConfig("/repo/api", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        assertThatThrownBy(() -> service.revert("a1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("origin/dev` in api")
                .hasMessageContaining("origin/<its deploy branch>` in gone");
    }

    @Test
    void undoesTheRepositoryThatIsLiveWithoutEvenLookingUpOneThatNeverLanded(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of()))
                .thenReturn("beef00991122");
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        Outcome outcome = service.revert("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.message()).contains("reverted api on dev (beef0099)", "REVERTED");
    }

    @Test
    void undoesTheRepositoriesInReverseOrderAndForgetsEachMergeItTookOut(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234")
                .withDeployCommit("web", "f00dfeed5678"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.revertMergeAndPush(any(), anyString(), anyString(), anyString(), anyList())).thenReturn("beef00991122");
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        Outcome outcome = service.revert("a1");

        InOrder undone = inOrder(git);
        undone.verify(git).revertMergeAndPush(Path.of("/repo/web"), "ABC-1", "dev", "f00dfeed5678", List.of());
        undone.verify(git).revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of());
        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.message()).contains("reverted web on dev", "api on dev", "REVERTED");
        assertThat(state.task("ABC-1").orElseThrow().repos()).extracting(TaskRepo::deployCommit)
                .containsOnlyNulls();
    }

    @Test
    void refusesTheUndoAsHalfDoneWhenOneRepositoryCouldNotBeReverted(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234")
                .withDeployCommit("web", "f00dfeed5678"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.revertMergeAndPush(Path.of("/repo/web"), "ABC-1", "dev", "f00dfeed5678", List.of()))
                .thenReturn("beef00991122");
        doThrow(new IllegalStateException("the revert conflicts with work done there since the deploy"))
                .when(git).revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of());
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        Outcome outcome = service.revert("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
        assertThat(outcome.message()).contains("reverted web on dev", "api still live on dev");
        assertThat(outcome.stamp()).contains("reverted web on dev", "api still live on dev");
        assertThat(state.task("ABC-1").orElseThrow().repos()).extracting(TaskRepo::deployCommit)
                .containsExactly("cafebabe1234", null);
    }

    @Test
    void discardsTheHalfMergeTheConflictLeftWhenTheDeployIsRevertedFromIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", new ProjectConfig("/repo/api", "origin/main", "dev", null), "web", new ProjectConfig("/repo/web", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);
        when(git.revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of())).thenReturn("beef00991122");
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        service.revert("a1");

        verify(git).discardDeploy(Path.of("/repo/web"), "ABC-1");
    }

    @Test
    void discardsTheConflictedMergeAndLandsTheUndoWhenNothingHadLanded(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj", new ProjectConfig("/repo", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/repo"), "ABC-1")).thenReturn(true);
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        Outcome outcome = service.revert("a1");

        verify(git).discardDeploy(Path.of("/repo"), "ABC-1");
        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.stamp()).contains("discarded the conflicted merge into dev");
    }

    @Test
    void keepsTheConflictWaitingWhenTheRevertOfWhatLandedIsRefused(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", new ProjectConfig("/repo/api", "origin/main", "dev", null), "web", new ProjectConfig("/repo/web", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        doThrow(new IllegalStateException("the revert conflicts"))
                .when(git).revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of());
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        assertThatThrownBy(() -> service.revert("a1")).hasMessage("the revert conflicts");
        verify(git, never()).discardDeploy(any(), anyString());
    }

    @Test
    void revertsWhatLandedWhenARepositoryThatNeverLandedHasLeftTheConfiguration(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("gone", "/gone-wt")), TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("api", "cafebabe1234"));
        ProjectConfig api = new ProjectConfig("/repo/api", "origin/main", "dev", null);
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(api);
        when(config.project("gone")).thenThrow(new IllegalArgumentException("Unknown project 'gone'"));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", api)));
        GitDeploy git = mock(GitDeploy.class);
        when(git.revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of())).thenReturn("beef00991122");
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        Outcome outcome = service.revert("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
    }

    @Test
    void discardsTheConflictedMergeOfASecondDeployAfterRevertingTheFirst(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).alias("a1")
                .deployCommit("cafebabe1234").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj", new ProjectConfig("/repo", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/repo"), "ABC-1")).thenReturn(true);
        when(git.revertMergeAndPush(Path.of("/repo"), "ABC-1", "dev", "cafebabe1234", List.of())).thenReturn("beef00991122");
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        service.revert("a1");

        verify(git).discardDeploy(Path.of("/repo"), "ABC-1");
    }

    @Test
    void refusesToUndoADeployOntoABranchThatIsTheBaseBranch(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployCommit("proj", "cafebabe1234"));
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "main", null));
        GitDeploy git = mock(GitDeploy.class);
        RevertService service = new RevertService(state, new DeployTargets(config, git), git);

        assertThatThrownBy(() -> service.revert("a1")).hasMessageContaining("deployBranch equals the base branch");
        verify(git, never()).revertMergeAndPush(any(), anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void refusesATaskThatIsNotThereNamingIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        GitDeploy git = mock(GitDeploy.class);
        RevertService service = new RevertService(state, new DeployTargets(mock(ConfigService.class), git), git);

        assertThatThrownBy(() -> service.revert("ABC-9"))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("No task ABC-9")
                .extracting("code").isEqualTo(Refusal.Code.NO_SUCH_TASK);
    }
}
