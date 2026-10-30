package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door,
                new TaskTurns(state, Clock.systemUTC()));

        relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed:\n- proj: 01234567 on dev", relay.standing("ABC-42"));

        verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev\n\nopen it in a browser");
    }

    interface Entry {
        boolean relayed(StepRelay relay, Standing read);
    }

    static Stream<Arguments> entries() {
        return Stream.of(
                Arguments.of("relay", (Entry) (relay, read) -> relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", read)),
                Arguments.of("relayRound", (Entry) (relay, read) -> relay.relayRound("ABC-42", Step.DEPLOY, "ABC-42 is deployed", read)),
                Arguments.of("relayUnder", (Entry) (relay, read) -> relay.relayUnder("ABC-42", Step.DEPLOY, "ABC-42 is deployed", false, read)),
                Arguments.of("write", (Entry) (relay, read) -> relay.write("ABC-42", Step.DEPLOY, "ABC-42 is deployed", read)),
                Arguments.of("say", (Entry) (relay, read) -> relay.say("ABC-42", Step.DEPLOY, "ABC-42 is deployed", read)),
                Arguments.of("relayComposed", (Entry) (relay, read) -> relay.relayComposed("ABC-42", List.of(Step.DEPLOY),
                        "ABC-42 is deployed", TaskStatus.REVIEWED, read)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entries")
    void relaysNothingWhenTheTurnMovedOnSinceTheTaskWasRead(String kind, Entry entry, @TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        Standing read = Standing.of(state.task("ABC-42").orElseThrow());
        TaskTurns turns = new TaskTurns(state, Clock.systemUTC());
        turns.opened("ABC-42", "answered", "the human answered", TaskStatus.DEPLOYED);
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        when(door.roundDue("ABC-42", "deploy")).thenReturn(true);
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door, turns);
        Turn before = turns.of("ABC-42");

        assertThatThrownBy(() -> entry.relayed(relay, read)).isInstanceOf(TaskMovedOn.class);

        verifyNoInteractions(sessions);
        assertThat(turns.of("ABC-42")).isEqualTo(before);
    }

    @Test
    void tellsTheSessionNothingWhenTheTaskMovedOnBetweenTheCheckAndTheClaim(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door,
                new TaskTurns(state, Clock.systemUTC()));
        StepRelay.Claim claim = new StepRelay.Claim(TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build(),
                () -> true, opens -> FlowReports.Claimed.MOVED_ON, opened -> false);

        assertThatThrownBy(() -> relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", claim))
                .isInstanceOf(TaskMovedOn.class);

        verify(sessions, never()).writeTaskContext(anyString(), anyString());
    }

    @Test
    void typesTheStepAsOneLine(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## wake\n\nContinue where\nyou stopped.\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.WAKE)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door,
                new TaskTurns(state, Clock.systemUTC()));

        relay.say("ABC-42", Step.WAKE, "The usage limit has reset.", relay.standing("ABC-42"));

        verify(sessions).say("ABC-42", "The usage limit has reset. Continue where you stopped.");
    }

    @Test
    void tellsTheFactsAloneAtAStepTheBriefLeavesEmpty(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door,
                new TaskTurns(state, Clock.systemUTC()));

        relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed:\n- proj: 01234567 on dev", relay.standing("ABC-42"));

        verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev");
    }

    @Test
    void relaysAStepOnceWhileTheSessionHoldsTheTurnItOpened(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, door, new TaskTurns(state, Clock.systemUTC()));
        relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

        boolean again = relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

        assertThat(again).isFalse();
        verify(sessions, times(1)).writeTaskContext("ABC-42", "ABC-42 is deployed");
    }

    @Test
    void sendsAStepOnceWhenJagtDiesWhileTellingTheSession(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        when(sessions.writeTaskContext("ABC-42", "ABC-42 is deployed")).thenThrow(new Error("jagt died"));
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, door, new TaskTurns(state, Clock.systemUTC()));
        assertThatThrownBy(() -> relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"))).isInstanceOf(Error.class);

        boolean again = relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

        assertThat(again).isFalse();
        verify(sessions, times(1)).writeTaskContext("ABC-42", "ABC-42 is deployed");
    }

    @Test
    void sendsAStepAgainWhenTellingTheSessionFailed(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        when(sessions.writeTaskContext("ABC-42", "ABC-42 is deployed"))
                .thenThrow(new IllegalStateException("window closed")).thenReturn("written");
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, door, new TaskTurns(state, Clock.systemUTC()));
        assertThatThrownBy(() -> relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42")))
                .isInstanceOf(IllegalStateException.class);

        boolean again = relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

        assertThat(again).isTrue();
    }

    @Test
    void stopsTheTaskForTheHumanWhenTellingTheSessionFailed(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        when(sessions.writeTaskContext("ABC-42", "ABC-42 is deployed"))
                .thenThrow(new IllegalStateException("window closed"));
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, door, new TaskTurns(state, Clock.systemUTC()));

        assertThatThrownBy(() -> relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42")))
                .isInstanceOf(IllegalStateException.class);

        verify(door).undelivered("ABC-42", "deploy", "java.lang.IllegalStateException: window closed");
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
                        new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

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
                        new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))),
                new TaskTurns(state, Clock.systemUTC()));

        boolean told = relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

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
                        new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        boolean told = OriginContext.as(ActionOrigin.BOARD, () -> relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42")));

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
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        boolean told = OriginContext.as(ActionOrigin.MCP, () -> relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42")));

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
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

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
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        relay.relayRound("ABC-42", Step.VERIFY, "BUILD FAILED in CartTest", relay.standing("ABC-42"));
        relay.relayRound("ABC-42", Step.VERIFY, "BUILD FAILED in OrderTest", relay.standing("ABC-42"));
        relay.relayRound("ABC-42", Step.VERIFY, "BUILD FAILED in CartTest again", relay.standing("ABC-42"));

        boolean told = relay.relayRound("ABC-42", Step.VERIFY, "BUILD FAILED in PaymentTest", relay.standing("ABC-42"));

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
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        List<Step> steps = List.of(Step.ROUND_THREADS, Step.ROUND_THREADS_DONE);
        relay.relayComposed("ABC-42", steps, "mia: Why is this loop not bounded?", TaskStatus.REVIEWED, relay.standing("ABC-42"));
        relay.relayComposed("ABC-42", steps, "mia: Why is this map not sorted?", TaskStatus.REVIEWED, relay.standing("ABC-42"));
        relay.relayComposed("ABC-42", steps, "mia: Why is this name so long?", TaskStatus.REVIEWED, relay.standing("ABC-42"));

        boolean told = relay.relayComposed("ABC-42", steps, "mia: Why is this test so short?", TaskStatus.REVIEWED, relay.standing("ABC-42"));

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
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        relay.relayRound("ABC-42", Step.VERIFY, "BUILD FAILED in CartTest", relay.standing("ABC-42"));
        relay.relayRound("ABC-42", Step.VERIFY, "BUILD FAILED in OrderTest", relay.standing("ABC-42"));

        boolean told = relay.relayRound("ABC-42", Step.VERIFY, "BUILD FAILED in PaymentTest", relay.standing("ABC-42"));

        assertThat(told).isTrue();
        assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
    }

    @Test
    void relaysTheMastersAnswerWhereItsSessionCaseSendsIt(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("session-cases.md"),
                "## answered\n\nstep: answered\nat: IN_PROGRESS\nby: master\nreplies: any; plain\nThe Master answers.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));

        boolean told = OriginContext.as(ActionOrigin.MASTER, () -> relay.relay("ABC-42", Step.ANSWERED, "Keep the old keys.", relay.standing("ABC-42")));

        assertThat(told).isTrue();
    }

    @Test
    void sendsTheTextWrittenWhereACaseSendsItAndOpensTheTurn(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door, new TaskTurns(state, Clock.systemUTC()));

        boolean told = relay.write("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

        assertThat(told).isEqualTo(true);
        verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed");
        assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(true);
    }

    @Test
    void leavesTheTextWrittenUnsentAndOpensNoTurnWhereNoCaseSendsIt(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(false, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door, new TaskTurns(state, Clock.systemUTC()));

        boolean told = relay.write("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

        assertThat(told).isEqualTo(false);
        verify(sessions, never()).writeTaskContext(anyString(), anyString());
        assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(false);
    }

    @Test
    void sendsTheLineTypedWhereACaseSendsItAndOpensTheTurn(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door, new TaskTurns(state, Clock.systemUTC()));

        boolean told = relay.say("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

        assertThat(told).isEqualTo(true);
        verify(sessions).say("ABC-42", "ABC-42 is deployed");
        assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(true);
    }

    @Test
    void leavesTheLineTypedUnsentAndOpensNoTurnWhereNoCaseSendsIt(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(false, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door, new TaskTurns(state, Clock.systemUTC()));

        boolean told = relay.say("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

        assertThat(told).isEqualTo(false);
        verify(sessions, never()).say(anyString(), anyString());
        assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(false);
    }

    @Test
    void sendsBelowAPendingInstructionWhereACaseSendsItAndOpensTheTurn(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door, new TaskTurns(state, Clock.systemUTC()));

        boolean told = relay.relayUnder("ABC-42", Step.DEPLOY, "ABC-42 is deployed", true, relay.standing("ABC-42"));

        assertThat(told).isEqualTo(true);
        verify(sessions).relayUnder("ABC-42", "ABC-42 is deployed", true);
        assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(true);
    }

    @Test
    void leavesBelowAPendingInstructionUnsentAndOpensNoTurnWhereNoCaseSendsIt(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        CaseDoor door = mock(CaseDoor.class);
        when(door.opens("ABC-42", Step.DEPLOY)).thenReturn(new CaseDoor.Admission(false, TaskStatus.REVIEWED));
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions, door, new TaskTurns(state, Clock.systemUTC()));

        boolean told = relay.relayUnder("ABC-42", Step.DEPLOY, "ABC-42 is deployed", true, relay.standing("ABC-42"));

        assertThat(told).isEqualTo(false);
        verify(sessions, never()).relayUnder(anyString(), anyString(), anyBoolean());
        assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(false);
    }
}
