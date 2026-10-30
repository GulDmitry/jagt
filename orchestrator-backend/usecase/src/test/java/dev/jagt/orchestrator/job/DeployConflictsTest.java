package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.DeployTargets;

import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeployConflictsTest {

    @Test
    void findsAConflictInTheRepositoryItsRecordNames(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT).deployment(new Deployment(1, List.of(
                Deployment.Target.of("api", "dev"), Deployment.Target.of("web", "dev").withConflict(Conflict.of(Deployment.Cause.MERGE, 1))), 1, null)).build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", new ProjectConfig("/repo/api", "origin/main", "dev", null), "web", new ProjectConfig("/src/web", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/src/web"), "ABC-1")).thenReturn(true);
        when(git.deployResolved(Path.of("/src/web"), "ABC-1", "dev")).thenReturn(true);
        when(git.deployProgress(Path.of("/src/web"), "ABC-1")).thenReturn(new Conflict.Progress(0, 2, "d"));
        DeployConflicts service = new DeployConflicts(state, new DeployTargets(config, git), git);

        var conflicts = service.waiting();

        assertThat(conflicts).containsExactly(Map.entry("ABC-1",
                new DeployConflicts.WaitingConflict(Path.of("/src/ABC-1-deploy"), true,
                        new Conflict.Progress(0, 2, "d"), null)));
    }

    @Test
    void namesTheRedPipelineAConflictWasHandedBackFor(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT)
                .message("red pipeline on the deploy commit: web 01234567 https://git.example.invalid/p/7")
                .deployment(new Deployment(1, List.of(Deployment.Target.of("web", "dev")
                        .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1))), 0, null)).build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("web",
                new ProjectConfig("/src/web", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/src/web"), "ABC-1")).thenReturn(true);
        when(git.deployProgress(Path.of("/src/web"), "ABC-1")).thenReturn(new Conflict.Progress(0, 0, "d"));
        DeployConflicts service = new DeployConflicts(state, new DeployTargets(config, git), git);

        var conflicts = service.waiting();

        assertThat(conflicts.get("ABC-1").red())
                .isEqualTo("red pipeline on the deploy commit: web 01234567 https://git.example.invalid/p/7");
    }
}
