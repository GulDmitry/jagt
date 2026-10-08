package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.port.Specs;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwnStatusReportsTest {

    @Test
    void refusesASessionsHandBackThatLeavesNoNotesForTheNextSession(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
        OwnStatusReports reports = new OwnStatusReports(state,
                new HandBackDue(mock(WorktreeChanges.class), mock(Specs.class)), statusReports);
        AgentStatusMessage handBack = new AgentStatusMessage("REVIEW_PENDING", "done", null, null, Map.of());

        assertThatThrownBy(() -> reports.report(handBack, "ABC-1"))
                .isInstanceOfSatisfying(Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE))
                .hasMessage("write task_notes.md before handing the round back");
    }

    @Test
    void refusesAHandBackWhoseChangeToTheSpecsDoesNotHold(@TempDir Path root) throws Exception {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        Files.writeString(root.resolve("task_notes.md"), "notes");
        WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
        when(worktreeChanges.agentFileLinesAdded(any())).thenReturn(Optional.of(0));
        Specs specs = mock(Specs.class);
        when(specs.owed(root, "ABC-1")).thenReturn(Optional.of("openspec/changes/abc-1 does not validate"));
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
        OwnStatusReports reports = new OwnStatusReports(state, new HandBackDue(worktreeChanges, specs), statusReports);
        AgentStatusMessage handBack = new AgentStatusMessage("REVIEW_PENDING", "done", null, null, Map.of());

        assertThatThrownBy(() -> reports.report(handBack, "ABC-1"))
                .isInstanceOfSatisfying(Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE))
                .hasMessage("[proj] openspec/changes/abc-1 does not validate");
    }

    @Test
    void refusesAHandBackWhoseAgentFileLinesGitCannotCount(@TempDir Path root) throws Exception {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        Files.writeString(root.resolve("task_notes.md"), "notes");
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
        OwnStatusReports reports = new OwnStatusReports(state,
                new HandBackDue(mock(WorktreeChanges.class), mock(Specs.class)), statusReports);
        AgentStatusMessage handBack = new AgentStatusMessage("REVIEW_PENDING", "done", null, null, Map.of());

        assertThatThrownBy(() -> reports.report(handBack, "ABC-1"))
                .isInstanceOfSatisfying(Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE))
                .hasMessage("jagt could not count the lines this task added to the project's agent file");
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
        OwnStatusReports reports = new OwnStatusReports(state,
                new HandBackDue(mock(WorktreeChanges.class), mock(Specs.class)), statusReports);

        String answer = reports.report(plan, "ABC-1");

        assertThat(answer).isEqualTo("Task ABC-1 -> PLAN_PENDING");
    }
}
