package dev.jagt.orchestrator.mcp;

import dev.jagt.orchestrator.agent.AgentRuntime;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.agent.ClaudeAgentRuntime;
import dev.jagt.orchestrator.agent.McpEndpoint;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.EditorDriver;
import dev.jagt.orchestrator.platform.TerminalDriver;
import dev.jagt.orchestrator.platform.UserNotifier;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.GitService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TmuxService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrchestratorToolsAutoReviewTest {

    @Test
    void markApprovedAdvancesTheStatusAndPingsTheHumanOnce(@TempDir Path root) {
        Fixture f = new Fixture(root);
        f.state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").build());

        f.tools.markApproved("ABC-1");

        assertThat(f.state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.APPROVED);
        verify(f.notifier).notify(eq("jagt · ABC-1"), contains("approved"));
    }

    @Test
    void markApprovedDoesNotRePingWhenAlreadyApproved(@TempDir Path root) {
        Fixture f = new Fixture(root);
        f.state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .alias("a1").mrUrl("http://mr/1").build());

        f.tools.markApproved("ABC-1");

        verify(f.notifier, never()).notify(eq("jagt · ABC-1"), contains("approved"));
    }

    @Test
    void stampsTheWindowStartWhenTheMrIsFirstLinked(@TempDir Path root) {
        Fixture f = new Fixture(root);
        f.state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());

        f.tools.updateAgentStatus("CI_POLLING", "MR: http://mr/1", "ABC-1", null);

        assertThat(f.state.task("ABC-1").orElseThrow().mrCreatedAt()).isPositive();
    }

    @Test
    void neverResetsTheWindowStartOnLaterRounds(@TempDir Path root) {
        Fixture f = new Fixture(root);
        f.state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
                .alias("a1").mrUrl("http://mr/1").mrCreatedAt(12345L).build());

        f.tools.updateAgentStatus("CI_POLLING", "MR: http://mr/1", "ABC-1", null);

        assertThat(f.state.task("ABC-1").orElseThrow().mrCreatedAt()).isEqualTo(12345L);
    }

    private static final class Fixture {
        final StateService state;
        final UserNotifier notifier = mock(UserNotifier.class);
        final OrchestratorTools tools;

        Fixture(Path root) {
            OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
            OrchestratorPaths paths = new OrchestratorPaths(properties);
            this.state = new StateService(new JsonMapper(), paths);
            ConfigService config = mock(ConfigService.class);
            when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
            this.tools = facade(config, state, mock(GitService.class), mock(TmuxService.class),
                    mock(EditorDriver.class), mock(TerminalDriver.class), notifier, properties);
        }
    }

    /**
     * The facade no longer takes the tmux/terminal/provisioning collaborators — it takes the two services the
     * split extracted. This wires them the way the Spring context does, so a facade test keeps saying what it
     * always said; a test of the extracted concerns builds only that concern (see AgentSessionsTest,
     * TaskProvisioningTest) and needs a fraction of this.
     */
    private static OrchestratorTools facade(ConfigService config, StateService state, GitService git,
                                            TmuxService tmux, EditorDriver editor, TerminalDriver terminal,
                                            UserNotifier notifier, OrchestratorProperties properties) {
        AgentRuntime runtime = new ClaudeAgentRuntime(properties, new McpEndpoint("http://localhost:8290/mcp"));
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, runtime);
        return new OrchestratorTools(config, state, git, editor, notifier, sessions,
                new TaskProvisioning(config, state, git, sessions, runtime, properties,
                        new OrchestratorPaths(properties), new PromptTemplates()));
    }
}
