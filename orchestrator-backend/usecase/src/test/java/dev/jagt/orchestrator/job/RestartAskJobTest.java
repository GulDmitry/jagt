package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestartAskJobTest {

    private final AgentSessions sessions = mock(AgentSessions.class);
    private final StepRelay relay = mock(StepRelay.class);

    private StateService state(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void asksARunningSessionHoldingAnOpenTurnToRepeatItsReport(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "round-not-ready", "digest", TaskStatus.REVIEW_PENDING);
        when(sessions.agentLive("ABC-42")).thenReturn(true);

        new RestartAskJob(state, sessions, relay).run();

        verify(relay).say("ABC-42", "restarted", "jagt was restarted.");
    }

    @Test
    void asksNothingOfATaskWhoseTurnIsNotTheSessions(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
        when(sessions.agentLive("ABC-42")).thenReturn(true);

        new RestartAskJob(state, sessions, relay).run();

        verify(relay, never()).say(anyString(), anyString(), anyString());
    }

    @Test
    void doesNotReviveASessionThatIsNotRunning(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
        when(sessions.agentLive("ABC-42")).thenReturn(false);

        new RestartAskJob(state, sessions, relay).run();

        verify(relay, never()).say(anyString(), anyString(), anyString());
    }

    @Test
    void doesNotTypeOverAHumansDraft(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
        when(sessions.agentLive("ABC-42")).thenReturn(true);
        when(sessions.composing("ABC-42")).thenReturn(true);

        new RestartAskJob(state, sessions, relay).run();

        verify(relay, never()).say(anyString(), anyString(), anyString());
    }

    @Test
    void keepsAskingTheOtherTasksWhenOneCannotBeTold(@TempDir Path root) {
        StateService state = state(root);
        TaskTurns turns = new TaskTurns(state, Clock.systemUTC());
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        state.putTask("ABC-43", TaskState.builder("proj", "/wt2", TaskStatus.IN_PROGRESS).build());
        turns.opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
        turns.opened("ABC-43", "start", "digest", TaskStatus.IN_PROGRESS);
        when(sessions.agentLive(anyString())).thenReturn(true);
        doThrow(new IllegalStateException("No session")).when(relay).say("ABC-42", "restarted", "jagt was restarted.");

        new RestartAskJob(state, sessions, relay).run();

        verify(relay).say("ABC-43", "restarted", "jagt was restarted.");
    }

    @Test
    void runsOnceAfterTheStartAndNotOnAnInterval(@TempDir Path root) {
        assertThat(new RestartAskJob(state(root), sessions, relay).every()).isNull();
    }
}
