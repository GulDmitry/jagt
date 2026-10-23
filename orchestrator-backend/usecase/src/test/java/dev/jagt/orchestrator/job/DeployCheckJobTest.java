package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployCheckJobTest {

    private final StateService state = mock(StateService.class);
    private final ConfigService config = mock(ConfigService.class);
    private final AgentSessions sessions = mock(AgentSessions.class);

    @Test
    void asksADeployedTasksSessionToCheckItWhereItLanded(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "0123456789abcdef")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        DeployCheckJob job = new DeployCheckJob(state, config, sessions,
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        job.run();

        verify(sessions).relayIfChanged(eq("ABC-42"), contains("- proj: 01234567 on dev"));
    }

    @Test
    void asksAsTheBriefsDeployStepSays(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n\nopen it in a browser\n");
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "0123456789abcdef")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        DeployCheckJob job = new DeployCheckJob(state, config, sessions,
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        job.run();

        verify(sessions).relayIfChanged(eq("ABC-42"), contains("open it in a browser"));
    }

    @Test
    void asksNothingWhereTheBriefsDeployStepIsEmpty(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "0123456789abcdef")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        DeployCheckJob job = new DeployCheckJob(state, config, sessions,
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        job.run();

        verify(sessions, never()).relayIfChanged(anyString(), anyString());
    }

    @Test
    void asksOncePerDeployEvenWhereAnotherRelayOverwroteTheAsk(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "0123456789abcdef")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(sessions.relayIfChanged(eq("ABC-42"), anyString())).thenReturn(true);
        DeployCheckJob job = new DeployCheckJob(state, config, sessions,
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        job.run();
        job.run();

        verify(sessions, times(1)).relayIfChanged(eq("ABC-42"), anyString());
    }

    @Test
    void asksNothingWhileTheFixOfAFailedCheckIsUnderway(@TempDir Path root) {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withDeployCommit("proj", "0123456789abcdef")));
        DeployCheckJob job = new DeployCheckJob(state, config, sessions,
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        job.run();

        verify(sessions, never()).relayIfChanged(anyString(), anyString());
    }

    @Test
    void namesAnEightCharacterCommitWhole(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "01234567")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        DeployCheckJob job = new DeployCheckJob(state, config, sessions,
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        job.run();

        verify(sessions).relayIfChanged(eq("ABC-42"), contains("- proj: 01234567 on dev"));
    }

    @Test
    void asksNothingOfADeployedTaskWhoseDeployLeftNoCommit(@TempDir Path root) {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        DeployCheckJob job = new DeployCheckJob(state, config, sessions,
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        job.run();

        verify(sessions, never()).relayIfChanged(anyString(), anyString());
    }
}
