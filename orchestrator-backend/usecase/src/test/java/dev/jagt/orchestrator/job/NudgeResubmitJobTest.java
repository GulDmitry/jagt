package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NudgeResubmitJobTest {

    @Test
    void marksTheTurnWhenItPressesEnterOnANudgeLeftInAnIdleInput(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskTurns turns = new TaskTurns(state, Clock.systemUTC());
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        turns.opened("ABC-42", "round", "digest", TaskStatus.REVIEWED);
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.resubmitUnsentNudge("ABC-42")).thenReturn(true);

        new NudgeResubmitJob(sessions, turns).runOn(state.tasks());

        assertThat(turns.of("ABC-42").mark(NudgeResubmitJob.MARK)).isEqualTo("1:1");
    }

    @Test
    void stopsPressingAfterAFewTimesWithinOneTurn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskTurns turns = new TaskTurns(state, Clock.systemUTC());
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        turns.opened("ABC-42", "round", "digest", TaskStatus.REVIEWED);
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.resubmitUnsentNudge("ABC-42")).thenReturn(true);
        NudgeResubmitJob job = new NudgeResubmitJob(sessions, turns);

        for (int tick = 0; tick < 8; tick++) {
            job.runOn(state.tasks());
        }

        verify(sessions, times(NudgeResubmitJob.CEILING)).resubmitUnsentNudge("ABC-42");
    }

    @Test
    void pressesNothingWhileTheTaskStandsStopped(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskTurns turns = new TaskTurns(state, Clock.systemUTC());
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .stop(new TaskStop(TaskStop.Trigger.PACE, 1, "evidence", null)).build());
        turns.opened("ABC-42", "round", "digest", TaskStatus.REVIEWED);
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.resubmitUnsentNudge("ABC-42")).thenReturn(true);

        new NudgeResubmitJob(sessions, turns).runOn(state.tasks());

        verify(sessions, never()).resubmitUnsentNudge("ABC-42");
    }

    @Test
    void readsNoWindowOfATaskWhoseTurnTheSessionDoesNotHold(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        AgentSessions sessions = mock(AgentSessions.class);

        new NudgeResubmitJob(sessions, new TaskTurns(state, Clock.systemUTC())).runOn(state.tasks());

        verify(sessions, never()).resubmitUnsentNudge("ABC-42");
    }
}
