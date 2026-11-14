package dev.jagt.orchestrator.job.session;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.presence.SessionLedger;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LaunchExitsTest {

  @TempDir Path root;
  private final AgentSessions sessions = mock(AgentSessions.class);
  private final WatchdogService watchdog = mock(WatchdogService.class);
  private final SessionLedger ledger = mock(SessionLedger.class);
  private StateService state;
  private LaunchExits exits;

  @BeforeEach
  void launchExitsOverStateOnDisk() {
    state = stateService().withRoot(root).build();
    exits = new LaunchExits(sessions, watchdog, ledger);
  }

  @Test
  void endsSessionThatExitedBeforeItsFirstReportWithWhatItPrinted() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    when(sessions.exitTail("ABC-42"))
        .thenReturn(Optional.of("Error: Invalid MCP configuration: bad"));

    exits.runOn(state.tasks());

    verify(watchdog).ended("ABC-42", "Error: Invalid MCP configuration: bad");
  }

  @Test
  void signsSessionWaitingAtLaunchOnPromptJagtNeverAnswersAsAskedByItsScreen() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    when(sessions.exitTail("ABC-42")).thenReturn(Optional.empty());
    when(sessions.unansweredPrompt("ABC-42"))
        .thenReturn(Optional.of("waiting on the trust dialog"));

    exits.runOn(state.tasks());

    verify(ledger)
        .sign(
            "ABC-42",
            new SessionSign.Asked("waiting on the trust dialog", SessionSign.Asked.Channel.SCREEN));
  }

  @Test
  void signsNothingForSessionWhoseReportAlreadyAsks() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.NEW).report(AgentReport.PERMISSION).build());
    when(sessions.exitTail("ABC-42")).thenReturn(Optional.empty());
    when(sessions.unansweredPrompt("ABC-42"))
        .thenReturn(Optional.of("waiting on the trust dialog"));

    exits.runOn(state.tasks());

    verifyNoInteractions(ledger);
  }

  @Test
  void readsNoWindowOfTaskPastItsFirstReport() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    exits.runOn(state.tasks());

    verifyNoInteractions(sessions);
  }
}
