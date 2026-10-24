package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StepRelayTest {

    private final AgentSessions sessions = mock(AgentSessions.class);

    @Test
    void tellsTheSessionTheStepsFactsThenWhatTheBriefAsks(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n\nopen it in a browser\n");
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", "deploy")).thenReturn(true);
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door);

        relay.relay("ABC-42", "deploy", "ABC-42 is deployed:\n- proj: 01234567 on dev");

        verify(sessions).relayIfChanged("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev\n\nopen it in a browser");
    }

    @Test
    void typesTheStepAsOneLine(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## wake\n\nContinue where\nyou stopped.\n");
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", "wake")).thenReturn(true);
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door);

        relay.say("ABC-42", "wake", "The usage limit has reset.");

        verify(sessions).say("ABC-42", "The usage limit has reset. Continue where you stopped.");
    }

    @Test
    void tellsTheFactsAloneAtAStepTheBriefLeavesEmpty(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", "deploy")).thenReturn(true);
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door);

        relay.relay("ABC-42", "deploy", "ABC-42 is deployed:\n- proj: 01234567 on dev");

        verify(sessions).relayIfChanged("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev");
    }

    @Test
    void relaysAStepACaseSendsAtTheTasksStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC())));

        relay.relay("ABC-42", "deploy", "ABC-42 is deployed");

        verify(sessions).relayIfChanged("ABC-42", "ABC-42 is deployed");
    }

    @Test
    void sendsNothingAndRecordsAMissWhereNoCaseSendsTheStepAtTheTasksStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))));

        boolean told = relay.relay("ABC-42", "deploy", "ABC-42 is deployed");

        assertThat(told).isFalse();
        verify(sessions, never()).relayIfChanged(anyString(), anyString());
        assertThat(Files.readString(root.resolve("misses.jsonl")))
                .isEqualTo("{\"at\":1000,\"task\":\"ABC-42\",\"step\":\"deploy\",\"status\":\"IN_PROGRESS\",\"by\":\"jagt\"}\n");
    }
}
