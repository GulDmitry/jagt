package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.SessionLedger;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LaunchExitsTest {

    @Test
    void endsASessionThatExitedBeforeItsFirstReportWithWhatItPrinted(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.exitTail("ABC-42")).thenReturn(Optional.of("Error: Invalid MCP configuration: bad"));
        WatchdogService watchdog = mock(WatchdogService.class);

        new LaunchExits(sessions, watchdog, mock(SessionLedger.class)).runOn(state.tasks());

        verify(watchdog).ended("ABC-42", "Error: Invalid MCP configuration: bad");
    }

    @Test
    void signsASessionWaitingAtLaunchOnAPromptJagtNeverAnswersAsAskedByItsScreen(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.exitTail("ABC-42")).thenReturn(Optional.empty());
        when(sessions.unansweredPrompt("ABC-42")).thenReturn(Optional.of("waiting on the trust dialog"));
        SessionLedger ledger = mock(SessionLedger.class);

        new LaunchExits(sessions, mock(WatchdogService.class), ledger).runOn(state.tasks());

        verify(ledger).sign("ABC-42", new SessionSign.Asked("waiting on the trust dialog", SessionSign.Asked.Channel.SCREEN));
    }

    @Test
    void signsNothingForASessionWhoseReportAlreadyAsks(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.NEW).report(AgentReport.PERMISSION).build());
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.exitTail("ABC-42")).thenReturn(Optional.empty());
        when(sessions.unansweredPrompt("ABC-42")).thenReturn(Optional.of("waiting on the trust dialog"));
        SessionLedger ledger = mock(SessionLedger.class);

        new LaunchExits(sessions, mock(WatchdogService.class), ledger).runOn(state.tasks());

        verifyNoInteractions(ledger);
    }

    @Test
    void readsNoWindowOfATaskPastItsFirstReport(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        AgentSessions sessions = mock(AgentSessions.class);

        new LaunchExits(sessions, mock(WatchdogService.class), mock(SessionLedger.class)).runOn(state.tasks());

        verifyNoInteractions(sessions);
    }
}
