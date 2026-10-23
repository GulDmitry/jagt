package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

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
    private final StepRelay relay = mock(StepRelay.class);
    private final DeployCheckJob job = new DeployCheckJob(state, config, relay);

    @Test
    void asksADeployedTasksSessionToCheckItWhereItLanded() {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "0123456789abcdef")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        job.run();

        verify(relay).relay(eq("ABC-42"), eq("deploy"), contains("- proj: 01234567 on dev"));
    }

    @Test
    void asksOncePerDeployEvenWhereAnotherRelayOverwroteTheAsk() {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "0123456789abcdef")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(relay.relay(eq("ABC-42"), eq("deploy"), anyString())).thenReturn(true);

        job.run();
        job.run();

        verify(relay, times(1)).relay(eq("ABC-42"), eq("deploy"), anyString());
    }

    @Test
    void asksNothingWhileTheFixOfAFailedCheckIsUnderway() {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withDeployCommit("proj", "0123456789abcdef")));

        job.run();

        verify(relay, never()).relay(anyString(), anyString(), anyString());
    }

    @Test
    void namesAnEightCharacterCommitWhole() {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "01234567")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        job.run();

        verify(relay).relay(eq("ABC-42"), eq("deploy"), contains("- proj: 01234567 on dev"));
    }

    @Test
    void asksNothingOfADeployedTaskWhoseDeployLeftNoCommit() {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        job.run();

        verify(relay, never()).relay(anyString(), anyString(), anyString());
    }
}
