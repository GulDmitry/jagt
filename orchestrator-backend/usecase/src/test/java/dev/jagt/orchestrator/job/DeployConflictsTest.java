package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.capability.deploy.DeployTargets;

import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeployConflictsTest {

    @Test
    void findsAConflictInTheRepositoryWhoseDeployWorktreeHoldsIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT).build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/src/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/src/web"), "ABC-1")).thenReturn(true);
        when(git.deployResolved(Path.of("/src/web"), "ABC-1", "dev")).thenReturn(true);
        DeployConflicts service = new DeployConflicts(state, new DeployTargets(config, git), git);

        var conflicts = service.waiting();

        assertThat(conflicts).containsExactly(java.util.Map.entry("ABC-1",
                new DeployConflicts.WaitingConflict(Path.of("/src/ABC-1-deploy"), true)));
    }
}
