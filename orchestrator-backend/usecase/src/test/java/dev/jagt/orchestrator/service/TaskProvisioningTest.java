package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskProvisioningTest {

    @TempDir
    Path root;

    private final ConfigService config = mock(ConfigService.class);
    private final NewTaskWorktrees worktrees = mock(NewTaskWorktrees.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private StateService state;
    private TaskProvisioning provisioning;

    @BeforeEach
    void setUp() {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString());
        state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        provisioning = new TaskProvisioning(config, state, worktrees, sessions);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    }

    @Test
    void rejectsUnknownModeBeforeCuttingAnything() {
        assertThatThrownBy(() -> provisioning.initializeTask(NewTask.builder("ABC-1", "proj").mode("bogus").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown mode");
        verifyNoInteractions(worktrees);
    }

    @Test
    void registersEveryProjectAndRunsTheSessionInTheFirst() {
        when(worktrees.cut(any(), any(), any())).thenReturn(List.of(
                new NewRepo("api", null, Path.of("/api"), Path.of("/ABC-7-api"), null, "origin/main", null, true),
                new NewRepo("web", null, Path.of("/web"), Path.of("/ABC-7-web"), null, "origin/main", null, false)));

        String answer = provisioning.initializeTask(NewTask.builder("ABC-7", "api").alsoIn(List.of("web")).build());

        assertThat(answer).contains("agent running on ABC-7", "also in web");
        assertThat(state.task("ABC-7").orElseThrow().projects()).containsExactly("api", "web");
        verify(sessions).startAgent("ABC-7", "a1", Path.of("/ABC-7-api"), false);
    }

    @Test
    void assignsNextFreeAliasWhenTicketLetterAlreadyInUse() {
        state.putTask("ABC-1", TaskState.builder("proj", "/first", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(worktrees.cut(any(), any(), any())).thenReturn(List.of(
                new NewRepo("proj", null, Path.of("/repo"), Path.of("/ABC-2-proj"), null, "origin/main", null, true)));

        provisioning.initializeTask(NewTask.builder("ABC-2", "proj").build());

        assertThat(state.task("ABC-2").orElseThrow().alias()).isEqualTo("a2");
    }

    @Test
    void aliasesATicketStartingWithIWithTheDottedIWhateverTheMachineLocale() {
        when(worktrees.cut(any(), any(), any())).thenReturn(List.of(
                new NewRepo("proj", null, Path.of("/repo"), Path.of("/INF-2-proj"), null, "origin/main", null, true)));

        provisioning.initializeTask(NewTask.builder("INF-2", "proj").build());

        assertThat(state.task("INF-2").orElseThrow().alias()).isEqualTo("i1");
    }

    @Test
    void remembersTheBaseBranchTheHumanNamedForTheReviewRequest() {
        when(worktrees.cut(any(), any(), any())).thenReturn(List.of(new NewRepo("proj", null, Path.of("/repo"),
                Path.of("/ABC-3-proj"), null, "feature/parent", null, true)));

        provisioning.initializeTask(NewTask.builder("ABC-3", "proj").baseBranch("origin/feature/parent").build());

        assertThat(state.task("ABC-3").orElseThrow().baseBranch()).isEqualTo("feature/parent");
    }

    @Test
    void leavesTheBaseBranchUnsetWhenTheHumanNamedNone() {
        when(worktrees.cut(any(), any(), any())).thenReturn(List.of(
                new NewRepo("proj", null, Path.of("/repo"), Path.of("/ABC-4-proj"), null, "origin/main", null, true)));

        provisioning.initializeTask(NewTask.builder("ABC-4", "proj").build());

        assertThat(state.task("ABC-4").orElseThrow().baseBranch()).isNull();
    }

    @Test
    void registersNothingWhenTheWorktreesCouldNotBeCut() {
        when(worktrees.cut(any(), any(), any())).thenThrow(new IllegalStateException("provisioning failed"));

        assertThatThrownBy(() -> provisioning.initializeTask(NewTask.builder("ABC-9", "proj").build()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(state.task("ABC-9")).isEmpty();
    }

    @Test
    void refusesToBuildATaskWithNoProjectForItsSession() {
        assertThatThrownBy(() -> NewTask.builder("ABC-1", " ").alsoIn(List.of("web")).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs the project its session runs in");
    }
}
