package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionLedgerTest {

    private final SessionProbe probe = mock(SessionProbe.class);

    @Test
    void writesTheSignOnTheTaskBesideTheProbesMemory(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        Clock clock = Clock.fixed(Instant.ofEpochMilli(7_000), ZoneOffset.UTC);

        new SessionLedger(probe, state, clock).record("ABC-1", SessionProbe.State.WORKING, null, null,
                new SessionSign.WorkStarted());

        verify(probe).report("ABC-1", SessionProbe.State.WORKING, null, 7_000);
        assertThat(state.task("ABC-1").orElseThrow().session().workStartedAt()).isEqualTo(7_000);
    }

    @Test
    void namesTheLogFileTheSessionAppendsTo(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));

        new SessionLedger(probe, state, Clock.systemUTC()).record("ABC-1", SessionProbe.State.IDLE, null,
                Path.of("/logs/session.jsonl"), new SessionSign.TurnEnded("/logs/session.jsonl"));

        verify(probe).logAt("ABC-1", Path.of("/logs/session.jsonl"));
    }
}
