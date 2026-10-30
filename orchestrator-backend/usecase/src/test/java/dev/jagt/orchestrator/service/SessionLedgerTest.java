package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.AgentReport;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SessionLedgerTest {

    private final Notifications notifications = mock(Notifications.class);

    @Test
    void writesTheSignOnTheTask(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        Clock clock = Clock.fixed(Instant.ofEpochMilli(7_000), ZoneOffset.UTC);

        new SessionLedger(state, notifications, clock).record("ABC-1", null, new SessionSign.WorkStarted());

        assertThat(state.task("ABC-1").orElseThrow().session().workStartedAt()).isEqualTo(7_000);
    }

    @Test
    void recordsTheLogFileTheSessionAppendsToOnTheTask(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

        new SessionLedger(state, notifications, Clock.systemUTC()).record("ABC-1",
                Path.of("/logs/session.jsonl"), new SessionSign.WorkStarted());

        assertThat(state.task("ABC-1").orElseThrow().session().log()).isEqualTo("/logs/session.jsonl");
    }

    @Test
    void raisesAPermissionOnTheTaskWhenAPromptBlocksTheSession(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

        new SessionLedger(state, notifications, Clock.systemUTC()).sign("ABC-1",
                new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK));

        assertThat(state.task("ABC-1").orElseThrow().report()).isEqualTo(AgentReport.PERMISSION);
    }

    @Test
    void tellsTheHumanOnceWithTheCauseWhenAPromptBlocksTheSessionAndNotAgainOnTheNextRead(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        SessionLedger ledger = new SessionLedger(state, notifications, Clock.systemUTC());
        SessionSign.Asked ask = new SessionSign.Asked("waiting on the trust dialog", SessionSign.Asked.Channel.SCREEN);

        ledger.sign("ABC-1", ask);
        ledger.sign("ABC-1", ask);

        verify(notifications, times(1)).send(Notification.watchdog("ABC-1", "agent needs you", "waiting on the trust dialog"));
    }

    @Test
    void tellsNobodyWhenTheSessionAlreadyAskedInItsOwnWords(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.QUESTION).build());

        new SessionLedger(state, notifications, Clock.systemUTC()).sign("ABC-1",
                new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK));

        verify(notifications, never()).send(any());
    }
}
