package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.EditorDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskRetirementTest {

    private final ConfigService config = mock(ConfigService.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final GitService git = mock(GitService.class);
    private final EditorDriver editor = mock(EditorDriver.class);

    private TaskRetirement retirement(StateService state) {
        return new TaskRetirement(state, config, git, editor, sessions);
    }

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void killsTheSessionBeforeDeletingTheDirectoryItIsRunningIn(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.DONE).alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        String result = retirement(state).retire("a1");

        // The other order leaves a zombie agent grinding in a deleted directory.
        var order = inOrder(sessions, git);
        order.verify(sessions).killWindows("ABC-1");
        order.verify(git).removeWorktree(any(), any(), any());
        assertThat(state.task("ABC-1")).isEmpty();
        assertThat(result).contains("Branch 'ABC-1' was kept");
    }

    @Test
    void leavesTheWorktreeOnDiskAndSaysSoWhenItsProjectIsGoneFromConfig(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("gone", "/wt", TaskStatus.DONE).alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        String result = retirement(state).retire("a1");

        assertThat(result).contains("worktree left on disk: project missing from config.json");
        verify(git, never()).removeWorktree(any(), any(), any());
        // The state entry still goes: a task whose project vanished cannot be worked on either way.
        assertThat(state.task("ABC-1")).isEmpty();
    }

    @Test
    void dropsTheDeadWorktreeFromTheEditorsRecentProjects(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.DONE).alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        retirement(state).retire("ABC-1");

        verify(editor).forgetProject(Path.of("/wt"));
    }
    @Test
    void deletesTheWorktreeOfEveryRepositoryTheTaskWorkedIn(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api-repo", "origin/main", "dev", List.of()),
                "web", new ProjectConfig("/web-repo", "origin/main", "dev", List.of()))));

        retirement(state).retire("a1");

        verify(git).removeWorktree(Path.of("/api-repo"), Path.of("/api-wt"), null);
        verify(git).removeWorktree(Path.of("/web-repo"), Path.of("/web-wt"), null);
        assertThat(state.task("ABC-1")).isEmpty();
    }
}
