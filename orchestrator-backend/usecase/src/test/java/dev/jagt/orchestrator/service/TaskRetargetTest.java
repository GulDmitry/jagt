package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskRetargetTest {

    @Test
    void recordsTheNewBaseOnceOriginHasItAndTheWorktreeStandsOnIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        GitWorktrees git = mock(GitWorktrees.class);
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(git.remoteBranchExists(Path.of("/wt"), "ABC-41")).thenReturn(true);
        when(git.standsOn(Path.of("/wt"), "ABC-41")).thenReturn(true);

        new TaskRetarget(state, git, config).retarget("ABC-42", "origin/ABC-41");

        assertThat(state.task("ABC-42").orElseThrow().baseBranch()).isEqualTo("ABC-41");
    }

    @Test
    void keepsTheBaseWhenTheWorktreeDoesNotStandOnTheBranchItNames(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        GitWorktrees git = mock(GitWorktrees.class);
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(git.remoteBranchExists(Path.of("/wt"), "ABC-41")).thenReturn(true);

        assertThatThrownBy(() -> new TaskRetarget(state, git, config).retarget("ABC-42", "ABC-41"))
                .isInstanceOf(Refusal.class);
        assertThat(state.task("ABC-42").orElseThrow().baseBranch()).isNull();
    }

    @Test
    void keepsTheBaseWhenOriginHasNoSuchBranch(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        GitWorktrees git = mock(GitWorktrees.class);
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(git.standsOn(Path.of("/wt"), "ABC-41")).thenReturn(true);

        assertThatThrownBy(() -> new TaskRetarget(state, git, config).retarget("ABC-42", "ABC-41"))
                .isInstanceOf(Refusal.class);
        assertThat(state.task("ABC-42").orElseThrow().baseBranch()).isNull();
    }

    @Test
    void takesTheRemoteOfTheProjectOffTheBranchItIsGiven(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        GitWorktrees git = mock(GitWorktrees.class);
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "upstream/main", "dev", List.of(), List.of(),
                null, null, null, null, null, null, null, "upstream", null, null));
        when(git.remoteBranchExists(Path.of("/wt"), "ABC-41")).thenReturn(true);
        when(git.standsOn(Path.of("/wt"), "ABC-41")).thenReturn(true);

        new TaskRetarget(state, git, config).retarget("ABC-42", "upstream/ABC-41");

        assertThat(state.task("ABC-42").orElseThrow().baseBranch()).isEqualTo("ABC-41");
    }
}
