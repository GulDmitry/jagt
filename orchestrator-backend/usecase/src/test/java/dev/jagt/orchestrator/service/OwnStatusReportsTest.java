package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OwnStatusReportsTest {

    @Test
    void refusesASessionsHandBackWhatTheWorktreeSaysIsOwed(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
        HandBackDue due = mock(HandBackDue.class);
        when(due.invalid(any())).thenReturn(Optional.empty());
        when(due.owed(any(), any())).thenReturn(Optional.of("write task_notes.md before handing the round back"));
        OwnStatusReports reports = new OwnStatusReports(state, due, statusReports);
        AgentStatusMessage handBack = new AgentStatusMessage("REVIEW_PENDING", "done", null, null, Map.of());

        assertThatThrownBy(() -> reports.report(handBack, "ABC-1"))
                .isInstanceOfSatisfying(Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE))
                .hasMessage("write task_notes.md before handing the round back");
    }

    @Test
    void asksTheSessionToFixAHandBackWhoseNotesAreInvalidRatherThanRefusingItForGood(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
        HandBackDue due = mock(HandBackDue.class);
        when(due.invalid(any())).thenReturn(Optional.of("task_notes.md names the model 'gpt'"));
        OwnStatusReports reports = new OwnStatusReports(state, due, statusReports);
        AgentStatusMessage handBack = new AgentStatusMessage("REVIEW_PENDING", "done", null, null, Map.of());

        assertThatThrownBy(() -> reports.report(handBack, "ABC-1"))
                .isNotInstanceOf(Refusal.class)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("task_notes.md names the model 'gpt'");
    }

    @Test
    void asksNoNotesOfAReportTheMasterDoesNotRead(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
        AgentStatusMessage working = new AgentStatusMessage("IN_PROGRESS", "still going", null, null, Map.of());
        when(statusReports.report(working, "ABC-1")).thenReturn("Task ABC-1 -> IN_PROGRESS");
        HandBackDue due = mock(HandBackDue.class);
        OwnStatusReports reports = new OwnStatusReports(state, due, statusReports);

        reports.report(working, "ABC-1");

        verifyNoInteractions(due);
    }

    @Test
    void passesAPlanThroughWithoutAskingForNotes(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
        AgentStatusMessage plan = new AgentStatusMessage("PLAN_PENDING", "plan ready", null, null, Map.of());
        when(statusReports.report(plan, "ABC-1")).thenReturn("Task ABC-1 -> PLAN_PENDING");
        OwnStatusReports reports = new OwnStatusReports(state, mock(HandBackDue.class), statusReports);

        String answer = reports.report(plan, "ABC-1");

        assertThat(answer).isEqualTo("Task ABC-1 -> PLAN_PENDING");
    }
}
