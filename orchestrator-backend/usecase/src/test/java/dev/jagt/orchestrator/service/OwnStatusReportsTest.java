package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OwnStatusReportsTest {

    private static ReportEffects effects(StateService state) {
        TaskTurns turns = new TaskTurns(state, Clock.systemUTC());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()))),
                Clock.systemUTC());
        return new ReportEffects(new SessionWakes(state, turns, Clock.systemUTC()), turns, stops,
                InfraWaitProperties.defaults());
    }


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
        OwnStatusReports reports = new OwnStatusReports(state, due, statusReports, mock(CaseDoor.class),
                effects(state));
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
        OwnStatusReports reports = new OwnStatusReports(state, due, statusReports, mock(CaseDoor.class),
                effects(state));
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
        CaseDoor door = mock(CaseDoor.class);
        when(door.takes("ABC-1", TaskStatus.IN_PROGRESS, AgentReport.PLAIN)).thenReturn(true);
        OwnStatusReports reports = new OwnStatusReports(state, due, statusReports, door,
                effects(state));

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
        CaseDoor door = mock(CaseDoor.class);
        when(door.takes("ABC-1", TaskStatus.PLAN_PENDING, AgentReport.PLAIN)).thenReturn(true);
        OwnStatusReports reports = new OwnStatusReports(state, mock(HandBackDue.class), statusReports, door,
                effects(state));

        String answer = reports.report(plan, "ABC-1");

        assertThat(answer).isEqualTo("Task ABC-1 -> PLAN_PENDING");
    }

    @Test
    void appliesAReplyACaseOfTheOpenTurnsStepTakes(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"), "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\n"
                + "replies: IN_PROGRESS; plain, question\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", root.toString(), TaskStatus.DEPLOYED).build());
        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "deploy", "a1b2", TaskStatus.REVIEWED);
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-42")).thenReturn(MessageContext.NONE);
        AgentStatusMessage broke = new AgentStatusMessage("IN_PROGRESS", "the page 500s", null, null, Map.of());
        OwnStatusReports reports = new OwnStatusReports(state, mock(HandBackDue.class), statusReports,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                effects(state));

        reports.report(broke, "ABC-42");

        verify(statusReports).report(broke, "ABC-42");
    }

    @Test
    void refusesAReplyNoCaseOfTheOpenTurnsStepTakesAndAppliesNothing(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"), "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\n"
                + "replies: IN_PROGRESS; plain, question\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", root.toString(), TaskStatus.DEPLOYED).build());
        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "deploy", "a1b2", TaskStatus.REVIEWED);
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-42")).thenReturn(MessageContext.NONE);
        AgentStatusMessage handBack = new AgentStatusMessage("REVIEW_PENDING", "fixed", null, null, Map.of());
        OwnStatusReports reports = new OwnStatusReports(state, mock(HandBackDue.class), statusReports,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                effects(state));

        assertThatThrownBy(() -> reports.report(handBack, "ABC-42"))
                .isInstanceOfSatisfying(Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
        verify(statusReports, never()).report(handBack, "ABC-42");
    }

    @Test
    void recordsARefusedReplyWithTheStepWhatCameAndWhatTheCasesTake(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"), "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\n"
                + "replies: IN_PROGRESS; plain, question\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", root.toString(), TaskStatus.DEPLOYED).build());
        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "deploy", "a1b2", TaskStatus.REVIEWED);
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-42")).thenReturn(MessageContext.NONE);
        AgentStatusMessage handBack = new AgentStatusMessage("REVIEW_PENDING", "fixed", null, null, Map.of());
        OwnStatusReports reports = new OwnStatusReports(state, mock(HandBackDue.class), statusReports,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                effects(state));

        assertThatThrownBy(() -> reports.report(handBack, "ABC-42")).isInstanceOf(Refusal.class);

        assertThat(Files.readString(root.resolve("misses.jsonl"))).isEqualTo("{\"at\":1000,\"task\":\"ABC-42\","
                + "\"step\":\"deploy\",\"status\":\"DEPLOYED\",\"by\":\"session\",\"got\":\"REVIEW_PENDING; plain\","
                + "\"expected\":\"IN_PROGRESS; plain, question\"}\n");
    }

    @Test
    void stopsTheTaskForTheHumanOnAReplyNoCaseTakes(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"), "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\n"
                + "replies: IN_PROGRESS; plain, question\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", root.toString(), TaskStatus.DEPLOYED).build());
        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "deploy", "a1b2", TaskStatus.REVIEWED);
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.contextFor("ABC-42")).thenReturn(MessageContext.NONE);
        AgentStatusMessage handBack = new AgentStatusMessage("REVIEW_PENDING", "fixed", null, null, Map.of());
        OwnStatusReports reports = new OwnStatusReports(state, mock(HandBackDue.class), statusReports,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                effects(state));

        assertThatThrownBy(() -> reports.report(handBack, "ABC-42")).isInstanceOf(Refusal.class);

        assertThat(state.task("ABC-42").orElseThrow())
                .extracting(task -> task.stop().trigger(), task -> task.turnOrNone().holder())
                .containsExactly(TaskStop.Trigger.MISS, Turn.Holder.HUMAN);
    }
}
