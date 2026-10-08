package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskRetirementTest {

    private final ConfigService config = mock(ConfigService.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final RetiredWorktrees worktrees = mock(RetiredWorktrees.class);

    @Test
    void killsTheSessionBeforeDeletingTheDirectoryItIsRunningIn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.DONE).alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TaskRetirement retirement = new TaskRetirement(state, config, sessions, worktrees);

        String result = retirement.retire("a1");

        var order = inOrder(sessions, worktrees);
        order.verify(sessions).killWindows("ABC-1");
        order.verify(worktrees).remove(anyString(), any(), any());
        assertThat(state.task("ABC-1")).isEmpty();
        assertThat(result).contains("Branch 'ABC-1' was kept");
    }

    @Test
    void saysTheWorktreeStayedOnDiskWhenItsProjectIsGoneFromConfig(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("gone", "/wt", TaskStatus.DONE).alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(worktrees.remove(anyString(), any(), any())).thenReturn(true);
        TaskRetirement retirement = new TaskRetirement(state, config, sessions, worktrees);

        String result = retirement.retire("a1");

        assertThat(result).contains("worktree left on disk: project missing from jagt.yml");
        assertThat(state.task("ABC-1")).isEmpty();
    }
}
