package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskStop;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StepRelayTest {

    private final AgentSessions sessions = mock(AgentSessions.class);

    @Test
    void tellsTheSessionTheStepsFactsThenWhatTheBriefAsks(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n\nopen it in a browser\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", "deploy")).thenReturn(true);
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door,
                new TaskTurns(state, Clock.systemUTC()));

        relay.relay("ABC-42", "deploy", "ABC-42 is deployed:\n- proj: 01234567 on dev");

        verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev\n\nopen it in a browser");
    }

    @Test
    void typesTheStepAsOneLine(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## wake\n\nContinue where\nyou stopped.\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", "wake")).thenReturn(true);
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door,
                new TaskTurns(state, Clock.systemUTC()));

        relay.say("ABC-42", "wake", "The usage limit has reset.");

        verify(sessions).say("ABC-42", "The usage limit has reset. Continue where you stopped.");
    }

    @Test
    void tellsTheFactsAloneAtAStepTheBriefLeavesEmpty(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", "deploy")).thenReturn(true);
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door,
                new TaskTurns(state, Clock.systemUTC()));

        relay.relay("ABC-42", "deploy", "ABC-42 is deployed:\n- proj: 01234567 on dev");

        verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev");
    }

    @Test
    void relaysAStepOnceWhileTheSessionHoldsTheTurnItOpened(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", "deploy")).thenReturn(true);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, door, new TaskTurns(state, Clock.systemUTC()));
        relay.relay("ABC-42", "deploy", "ABC-42 is deployed");

        boolean again = relay.relay("ABC-42", "deploy", "ABC-42 is deployed");

        assertThat(again).isFalse();
        verify(sessions, times(1)).writeTaskContext("ABC-42", "ABC-42 is deployed");
    }

    @Test
    void relaysAStepACaseSendsAtTheTasksStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\nreplies: any; plain\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        relay.relay("ABC-42", "deploy", "ABC-42 is deployed");

        verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed");
    }

    @Test
    void sendsNothingAndRecordsAMissWhereNoCaseSendsTheStepAtTheTasksStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\nreplies: any; plain\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)),
                        new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))),
                new TaskTurns(state, Clock.systemUTC()));

        boolean told = relay.relay("ABC-42", "deploy", "ABC-42 is deployed");

        assertThat(told).isFalse();
        verify(sessions, never()).writeTaskContext(anyString(), anyString());
        assertThat(Files.readString(root.resolve("misses.jsonl")))
                .isEqualTo("{\"at\":1000,\"task\":\"ABC-42\",\"step\":\"deploy\",\"status\":\"IN_PROGRESS\",\"by\":\"jagt\"}\n");
    }

    @Test
    void sendsNothingAndRecordsAMissWhereTheStepWasSetOffBySomeoneItsCaseDoesNotName(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\nreplies: any; plain\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)),
                        new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        boolean told = OriginContext.as(ActionOrigin.BOARD, () -> relay.relay("ABC-42", "deploy", "ABC-42 is deployed"));

        assertThat(told).isFalse();
        verify(sessions, never()).writeTaskContext(anyString(), anyString());
        assertThat(Files.readString(root.resolve("misses.jsonl")))
                .isEqualTo("{\"at\":1000,\"task\":\"ABC-42\",\"step\":\"deploy\",\"status\":\"DEPLOYED\",\"by\":\"human\"}\n");
    }

    @Test
    void relaysAStepASessionsOwnCallSetsOffAsJagtsAnswerToIt(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\nreplies: any; plain\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        boolean told = OriginContext.as(ActionOrigin.MCP, () -> relay.relay("ABC-42", "deploy", "ABC-42 is deployed"));

        assertThat(told).isTrue();
    }

    @Test
    void stopsTheTaskForTheHumanWhereNoCaseSendsTheStepAtItsStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## deploy\n\nstep: deploy\nat: DEPLOYED\nby: jagt\nreplies: any; plain\nThe task is live.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        relay.relay("ABC-42", "deploy", "ABC-42 is deployed");

        assertThat(state.task("ABC-42").orElseThrow().stop())
                .extracting(TaskStop::trigger, TaskStop::evidence)
                .containsExactly(TaskStop.Trigger.MISS, "no case sends deploy at IN_PROGRESS, set off by jagt");
    }

    @Test
    void stopsTheTaskInsteadOfSendingAFourthRedVerificationRunning(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## verify\n\nstep: verify\nat: VERIFYING\nby: jagt\nreplies: any; plain\nThe command failed.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        relay.relayRound("ABC-42", "verify", "BUILD FAILED in CartTest");
        relay.relayRound("ABC-42", "verify", "BUILD FAILED in OrderTest");
        relay.relayRound("ABC-42", "verify", "BUILD FAILED in CartTest again");

        boolean told = relay.relayRound("ABC-42", "verify", "BUILD FAILED in PaymentTest");

        assertThat(told).isFalse();
        verify(sessions, times(3)).writeTaskContext(anyString(), anyString());
        assertThat(state.task("ABC-42").orElseThrow().stop())
                .extracting(TaskStop::trigger, TaskStop::evidence)
                .containsExactly(TaskStop.Trigger.ROUNDS, "verify sent 3 rounds running in 24h; the next one was not");
    }

    @Test
    void stopsTheTaskInsteadOfSendingAFourthReviewRoundRunning(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        List<String> steps = List.of("round-threads", "round-threads-done");
        relay.relayComposed("ABC-42", steps, "mia: Why is this loop not bounded?");
        relay.relayComposed("ABC-42", steps, "mia: Why is this map not sorted?");
        relay.relayComposed("ABC-42", steps, "mia: Why is this name so long?");

        boolean told = relay.relayComposed("ABC-42", steps, "mia: Why is this test so short?");

        assertThat(told).isFalse();
        verify(sessions, times(3)).relayFresh(anyString(), anyString());
        assertThat(state.task("ABC-42").orElseThrow().stop())
                .extracting(TaskStop::trigger, TaskStop::evidence)
                .containsExactly(TaskStop.Trigger.ROUNDS,
                        "round-threads+round-threads-done sent 3 rounds running in 24h; the next one was not");
    }

    @Test
    void sendsAThirdRedVerificationRunning(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## verify\n\nstep: verify\nat: VERIFYING\nby: jagt\nreplies: any; plain\nThe command failed.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        relay.relayRound("ABC-42", "verify", "BUILD FAILED in CartTest");
        relay.relayRound("ABC-42", "verify", "BUILD FAILED in OrderTest");

        boolean told = relay.relayRound("ABC-42", "verify", "BUILD FAILED in PaymentTest");

        assertThat(told).isTrue();
        assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
    }

    @Test
    void sendsNothingTheMasterRelaysWhereNoMasterCaseAllowsIt(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## answered\n\nstep: answered\nat: any\nby: master\nreplies: any; plain\nThe Master answers.\n");
        Files.writeString(root.resolve("master-cases.md"),
                "## answered\n\nmove: answered\nat: REVIEW_PENDING\nby: master\nThe Master answers.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        boolean told = OriginContext.as(ActionOrigin.MASTER, () -> relay.relay("ABC-42", "answered", "Keep the old keys."));

        assertThat(told).isFalse();
        verify(sessions, never()).writeTaskContext(anyString(), anyString());
        assertThat(state.task("ABC-42").orElseThrow().stop())
                .extracting(TaskStop::evidence).isEqualTo("no master case allows answered at IN_PROGRESS");
    }
}
