package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrustedGitStateTest {

    private final GitWorktrees git = mock(GitWorktrees.class);

    @Test
    void trustsWhatItFindsForATaskWithNothingRecorded(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build());
        when(git.gitState(List.of(Path.of("/wt")))).thenReturn(Map.of("core.editor in /r/.git/config", "a1"));

        Optional<String> distrust = new TrustedGitState(state, git).distrust("ABC-42");

        assertThat(distrust).isEmpty();
        assertThat(state.task("ABC-42").orElseThrow().gitTrust())
                .isEqualTo(GitTrust.of(Map.of("core.editor in /r/.git/config", "a1")));
    }

    @Test
    void parksTheTaskNamingTheKeyAndFileThatChanged(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .gitTrust(GitTrust.of(Map.of())).build());
        when(git.gitState(List.of(Path.of("/wt")))).thenReturn(Map.of("core.fsmonitor in /r/.git/config", "a1"));

        Optional<String> distrust = new TrustedGitState(state, git).distrust("ABC-42");

        assertThat(distrust).hasValueSatisfying(why -> assertThat(why).contains("core.fsmonitor in /r/.git/config added"));
        assertThat(state.task("ABC-42").orElseThrow().gitStateChanged()).isTrue();
    }

    @Test
    void countsAChangeFoundAsTheTasksGitStateRefusal(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .gitTrust(GitTrust.of(Map.of())).build());
        when(git.gitState(List.of(Path.of("/wt")))).thenReturn(Map.of("core.fsmonitor in /r/.git/config", "a1"));

        new TrustedGitState(state, git).distrust("ABC-42");

        assertThat(state.task("ABC-42").orElseThrow().refusals()).containsEntry(Guard.GIT_STATE, 1);
    }

    @Test
    void keepsAParkedTaskParkedWithoutReadingGitAgain(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .gitTrust(GitTrust.of(Map.of()).withChanged("core.fsmonitor in /r/.git/config added")).build());

        Optional<String> distrust = new TrustedGitState(state, git).distrust("ABC-42");

        assertThat(distrust).contains("core.fsmonitor in /r/.git/config added");
        verifyNoInteractions(git);
    }

    @Test
    void refusesWithoutParkingWhereGitCannotBeRead(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .gitTrust(GitTrust.of(Map.of())).build());
        when(git.gitState(any())).thenThrow(new IllegalStateException("git rev-parse in /wt failed"));

        Optional<String> distrust = new TrustedGitState(state, git).distrust("ABC-42");

        assertThat(distrust).contains("git state of ABC-42 could not be read: git rev-parse in /wt failed");
        assertThat(state.task("ABC-42").orElseThrow().gitStateChanged()).isFalse();
    }

    @Test
    void trustAdoptsWhatChangedAndUnparksTheTask(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .gitTrust(GitTrust.of(Map.of()).withChanged("core.fsmonitor in /r/.git/config added")).build());
        when(git.gitState(List.of(Path.of("/wt")))).thenReturn(Map.of("core.fsmonitor in /r/.git/config", "a1"));

        new TrustedGitState(state, git).trust("ABC-42");

        assertThat(state.task("ABC-42").orElseThrow().gitTrust())
                .isEqualTo(GitTrust.of(Map.of("core.fsmonitor in /r/.git/config", "a1")));
    }
}
