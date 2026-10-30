package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NewTaskWorktrees;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.ProjectConfig;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoAttachmentTest {

    @TempDir
    Path root;

    private final ConfigService config = mock(ConfigService.class);
    private final NewTaskWorktrees worktrees = mock(NewTaskWorktrees.class);
    private final StepRelay relay = mock(StepRelay.class);
    private StateService state;
    private RepoAttachment attachment;

    private final NewRepo registry = new NewRepo("registry", new ProjectConfig("/reg", "origin/main", null, List.of()),
            Path.of("/reg"), Path.of("/wt/ABC-7-registry"), Path.of("/reg/.git"), "origin/main", "url", false);

    @BeforeEach
    void setUp() {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString());
        state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        attachment = new RepoAttachment(config, state, worktrees, relay);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    }

    @Test
    void tellsTheSessionWhereTheRepositoryItAskedForNowIs() {
        state.putTask("ABC-7", TaskState.builder("api", "/wt/ABC-7-api", TaskStatus.IN_PROGRESS).alias("a1")
                .repoRequest("registry").build());
        when(worktrees.attach(eq("ABC-7"), eq("registry"), any())).thenReturn(registry);

        String said = attachment.attach("ABC-7");

        verify(relay).relayUnder(eq("ABC-7"), eq("repo"), contains("/wt/ABC-7-registry"), eq(false), any(Standing.class));
        assertThat(said).contains("the session was told");
    }

    @Test
    void saysSoWhenTheRepositoryIsAddedButTheSessionCouldNotBeTold() {
        state.putTask("ABC-7", TaskState.builder("api", "/wt/ABC-7-api", TaskStatus.IN_PROGRESS).alias("a1")
                .repoRequest("registry").build());
        when(worktrees.attach(eq("ABC-7"), eq("registry"), any())).thenReturn(registry);
        doThrow(new IllegalStateException("no window")).when(relay).relayUnder(any(), any(), any(), anyBoolean(), any(Standing.class));

        String said = attachment.attach("ABC-7");

        assertThat(said).contains("/wt/ABC-7-registry").contains("could not be told").contains("no window");
    }

    @Test
    void refusesATaskThatAskedForNoRepositoryWithoutCuttingAnything() {
        state.putTask("ABC-7", TaskState.builder("api", "/wt/ABC-7-api", TaskStatus.IN_PROGRESS).alias("a1").build());

        assertThatThrownBy(() -> attachment.attach("ABC-7")).isInstanceOf(Refusal.class);

        verify(worktrees, never()).attach(any(), any(), any());
    }
}
