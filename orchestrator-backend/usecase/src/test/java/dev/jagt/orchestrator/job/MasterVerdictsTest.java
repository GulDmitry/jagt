package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.MissLog;
import dev.jagt.orchestrator.service.PlanApproval;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.task.LoopWatch;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterVerdictsTest {

    private final AgentSessions sessions = mock(AgentSessions.class);
    private final MasterShip ship = mock(MasterShip.class);
    private final MasterDecisions decisions = spy(new MasterDecisions(mock(WorktreeChanges.class)));

    @Test
    void sendsARoundThatIsNotReadyBackToWhoeverWroteTheCode(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 1, ""), acting);

        verify(sessions).writeTaskContext(eq("ABC-1"), contains("the guard is inverted"));
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void takesANotReadyRoundBackToWorkSoTheFixedOneIsReadAgain(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 1, ""), acting);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void signsTheMoveItsVerdictTookAsTheMasters(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 1, ""), acting);

        assertThat(state.task("ABC-1").orElseThrow().history().getLast().origin()).isEqualTo(ActionOrigin.MASTER);
    }

    @Test
    void recordsAMissItsVerdictSetOffAsTheMasters(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.CI_POLLING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 1, ""), acting);

        assertThat(Files.readString(root.resolve("misses.jsonl")))
                .contains("\"step\":\"round-not-ready\"", "\"by\":\"master\"");
    }

    @Test
    void putsTheSessionBackToWorkOnTheAnswerTheMasterGaveIt(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.answered("ABC-1", task, "keep v2?", new MasterPanel.Decision("keep v2 beside v3", ""), false);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow())
                .returns(TaskStatus.IN_PROGRESS, TaskState::status)
                .returns("master answered the question", TaskState::message);
    }

    @Test
    void keepsTheDeployConflictHandOffStandingUnderTheAnswerToAQuestion(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.DEPLOY_CONFLICT).alias("a1").build();
        state.putTask("ABC-42", task);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.answered("ABC-42", task, "keep both imports?", new MasterPanel.Decision("keep both imports", ""), false);

        verify(sessions).relayUnder(eq("ABC-42"), contains("keep both imports"), eq(false));
        verify(sessions, never()).writeTaskContext(eq("ABC-42"), anyString());
    }

    @Test
    void restartsTheSessionWithFreshToolsWhenItsAnswersChangedNothing(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.answered("ABC-1", task, "override the gate?", new MasterPanel.Decision("refactor the shared block", ""), true);

        verify(sessions).relayFresh(eq("ABC-1"), contains("refactor the shared block"));
    }

    @Test
    void settlesWhatANotReadyRoundSentBackSoTheNextRoundDoesNotReopenIt(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 1, ""), acting);

        verify(decisions).record(task, "- [developer] Foo.java — the guard is inverted (behaviour)");
    }

    @Test
    void relaysAFindingAnEarlierRoundMadeMarkedAsARepeat(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        decisions.returned(task, 3, List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "unguarded delay", "blocking", "delay", "test"))));
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "unguarded delay", "blocking", "delay", "test"))), List.of(), List.of(), 7, ""), acting);

        verify(sessions).writeTaskContext(eq("ABC-1"),
                contains("- [qa] Foo.java delay — the delay is never pinned (unguarded delay, test) [repeat]"));
    }

    @Test
    void asksTheHumanWhenRoundsOfRepeatsKeepComingBack(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        decisions.returned(task, 3, List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "unguarded delay", "blocking", "delay", "test"))));
        decisions.returned(task, 5, List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "unguarded delay", "blocking", "delay", "test"))));
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "unguarded delay", "blocking", "delay", "test"))), List.of(), List.of(), 7, ""), acting);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow())
                .returns(AgentReport.QUESTION, TaskState::report)
                .returns("reviewer: These findings came back 2 rounds running, neither fixed nor disputed:"
                        + " - [qa] Foo.java delay — the delay is never pinned (unguarded delay, test) [repeat]",
                        TaskState::message);
    }

    @Test
    void settlesNoRequestForEvidenceSoTheSessionsAnswerCanStillChangeIt(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("chaplain", new Finding("Api.java", "which caller still reads v2", "",
                        "unproven", "", "behaviour")), new MasterReview.Said("developer", new Finding("Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 1, ""), acting);

        verify(decisions).record(task, "- [developer] Foo.java — the guard is inverted (behaviour)");
    }

    @Test
    void shipsAReadyRoundOnlyWhereAHumanSaidTheReviewerStandsInForThem(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(ship.ship("ABC-1", task)).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
                acting);

        assertThat(moved).isTrue();
        verify(ship).ship("ABC-1", task);
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void shipsNothingWhereTheHumanKeptThatStep(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var kept = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("ship"), null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
                kept);

        assertThat(moved).isFalse();
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void leavesAReadyRoundForTheHumanWhileTheReviewerOnlyJudges(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var judging = new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
                judging);

        assertThat(moved).isFalse();
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void startsTheSessionOnAPlanTheMasterFoundHolds(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
        state.putTask("ABC-1", planned);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
                acting);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow())
                .returns(TaskStatus.IN_PROGRESS, TaskState::status)
                .returns("master: the plan holds", TaskState::message);
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void opensTheGateOfAnAutoplannedTaskWhenTheMasterFindsThePlanHolds(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.CLOSED).build();
        state.putTask("ABC-1", planned);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""), acting);

        assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.OPEN);
    }

    @Test
    void leavesAPlanAskedForInPlanModeForTheHumanThoughTheMasterMayApprovePlans(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.REQUIRED).build();
        state.putTask("ABC-1", planned);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", planned,
                new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""), acting);

        assertThat(moved).isFalse();
        assertThat(state.task("ABC-1").orElseThrow())
                .returns(TaskStatus.PLAN_PENDING, TaskState::status)
                .returns(PlanGate.REQUIRED, TaskState::planGate);
    }

    @Test
    void keepsThePlanGateClosedWhenTheMasterSendsThePlanBack(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.REQUIRED).build();
        state.putTask("ABC-1", planned);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("planner", new Finding("plan.md", "misses the migration", "", "wrong", "",
                        "missing"))), List.of(), List.of(), 1, ""), acting);

        assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
    }

    @Test
    void leavesAPlanThatHoldsForTheHumanWhoKeptThatStep(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
        state.putTask("ABC-1", planned);
        var kept = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("plan"), null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
                kept);

        assertThat(moved).isFalse();
        verify(sessions, never()).writeTaskContext(anyString(), anyString());
    }

    @Test
    void answersThePlansQuestionWithTheDecisionBeforeStartingTheSession(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
                .message("admins only or every user?").report(AgentReport.QUESTION).build();
        state.putTask("ABC-1", planned);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(),
                List.of(new MasterReview.Said("planner", new Finding("", "the endpoint is for admins only, as the ticket says",
                        "decided", "noise", "", "scope"))), List.of(), 1, ""), acting);

        verify(sessions).writeTaskContext(eq("ABC-1"), contains("answered your question"));
        verify(sessions).writeTaskContext(eq("ABC-1"), contains("the endpoint is for admins only, as the ticket says"));
        verify(sessions).writeTaskContext(eq("ABC-1"), contains("the plan holds."));
    }

    @Test
    void sendsAPlanThatMissesTheTicketBackToBeReworked(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md.dist"), "## plan-not-ready\n\nrework the plan\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
        state.putTask("ABC-1", planned);
        var judging = new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("planner", new Finding("plan.md", "drops the v2 route the ticket keeps", "",
                        "wrong", "", "missing"))), List.of(), List.of(), 1, ""), judging);

        verify(sessions).writeTaskContext("ABC-1", "- [planner] plan.md — drops the v2 route the ticket keeps (missing)\n\nrework the plan");
    }

    @Test
    void putsTheReviewersQuestionToTheHumanThroughTheSessionThatWaitsOnTheAnswer(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.QUESTION, List.of(), List.of(),
                List.of("Add v3 beside v2, or replace it?"), 1, ""), acting);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow())
                .returns(TaskStatus.IN_PROGRESS, TaskState::status)
                .returns(AgentReport.QUESTION, TaskState::report)
                .returns("reviewer: Add v3 beside v2, or replace it?", TaskState::message);
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void asksTheSameQuestionOnce(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));
        relay.relay("ABC-1", "asked-human", "The reviewer asked the human: Add v3 beside v2, or replace it?");

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.QUESTION, List.of(), List.of(),
                List.of("Add v3 beside v2, or replace it?"), 1, ""), acting);

        assertThat(moved).isFalse();
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void asksTheHumanNothingWhenTheSessionAlreadyHoldsTheStalledRoundsQuestion(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        decisions.returned(task, 3, List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "", "blocking", "delay", "test"))));
        decisions.returned(task, 5, List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "", "blocking", "delay", "test"))));
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));
        relay.relay("ABC-1", "asked-human", "The reviewer asked the human: These findings came back 2 rounds running,"
                + " neither fixed nor disputed: - [qa] Foo.java delay — the delay is never pinned (test) [repeat]");

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "", "blocking", "delay", "test"))), List.of(), List.of(), 7, ""), acting);

        assertThat(moved).isFalse();
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void countsARoundAsRepeatedOnlyWhenEveryFindingRepeatsAnEarlierOne(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        decisions.returned(task, 3, List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "", "blocking", "delay", "test"))));
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "", "blocking", "delay", "test")),
                        new MasterReview.Said("developer", new Finding("Bar.java", "a new finding", "", "blocking", "", "behaviour"))),
                List.of(), List.of(), 7, ""),
                acting);

        assertThat(decisions.repeatedRounds(task)).isZero();
    }

    @Test
    void recordsWhatTheMasterDecidedSoALaterRoundDoesNotReopenIt(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.answered("ABC-1", task, "keep v2?", new MasterPanel.Decision("keep v2\nbeside v3", ""), false);

        verify(decisions).record(task, "- asked: keep v2? — decided: keep v2 beside v3");
        verify(decisions).answered(task);
    }

    @Test
    void answersNothingAndRecordsNothingWhenTheSessionAlreadyHoldsThatAnswer(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));
        relay.relay("ABC-1", "answered", "The Master answered your question, standing in for the human:\nkeep v2 beside v3");

        boolean moved = verdicts.answered("ABC-1", task, "keep v2?", new MasterPanel.Decision("keep v2 beside v3", ""), false);

        assertThat(moved).isFalse();
        verify(decisions, never()).record(any(), anyString());
    }

    @Test
    void opensNoTaskWhenTheMastersAnswerHoldsADoLine(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        verdicts.answered("ABC-1", task, "a second request?",
                new MasterPanel.Decision("drop the old keys here", "do proj drop the old keys from main"), false);

        verify(sessions).writeTaskContext(eq("ABC-1"), contains("Not opened: only the human opens a task."));
    }

    @Test
    void reportsAnAnswerCountOverTheTreeAsTheDecisionsHoldIt(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        doReturn(3).when(decisions).answersOverThisTree(task);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        assertThat(verdicts.answersOverThisTree(task)).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"1,false", "2,true", "3,true"})
    void callsAReviewStalledFromTheSecondRepeatedRoundOn(int repeated, boolean stalled, @TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        doReturn(repeated).when(decisions).repeatedRounds(task);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        assertThat(verdicts.stalled(task)).isEqualTo(stalled);
    }

    @ParameterizedTest
    @CsvSource({"NOT_READY,REVIEW_PENDING", "QUESTION,REVIEW_PENDING", "READY,PLAN_PENDING"})
    void movesNothingOfATaskThatIsGoneFromTheState(MasterReview.Kind kind, TaskStatus status, @TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), status).alias("a1").build();
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(kind, List.of(new MasterReview.Said("developer",
                new Finding("Foo.java", "wrong", "", "blocking", "", "behaviour"))), List.of(), List.of(), 1, ""),
                acting);

        assertThat(moved).isFalse();
    }

    @Test
    void movesNothingOfAnAnsweredTaskThatIsGoneFromTheState(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.answered("ABC-1", task, "keep v2?", new MasterPanel.Decision("keep v2 beside v3", ""), false);

        assertThat(moved).isFalse();
    }

    @Test
    void movesNothingWhenTheShipWasNotOpened(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
                acting);

        assertThat(moved).isFalse();
    }

    @Test
    void startsNothingOnAPlanTheSessionWasAlreadyToldHolds(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
        state.putTask("ABC-1", planned);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));

        boolean moved = verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
                acting);

        assertThat(moved).isFalse();
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.PLAN_PENDING);
    }

    @Test
    void stopsTheTaskInsteadOfReturningAFourthRoundOfFreshFindingsRunning(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build());
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));
        verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Cart.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 1, ""), acting);
        state.updateTask("ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 1));
        verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Order.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 2, ""), acting);
        state.updateTask("ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 2));
        verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Payment.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 3, ""), acting);
        state.updateTask("ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 3));

        boolean moved = verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Invoice.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 4, ""), acting);

        assertThat(moved).isFalse();
        assertThat(state.task("ABC-1").orElseThrow())
                .extracting(TaskState::status, done -> done.loopOrNone().stop().trigger())
                .containsExactly(TaskStatus.REVIEW_PENDING, LoopWatch.Trigger.ROUNDS);
    }

    @Test
    void returnsAThirdRoundOfFreshFindingsRunning(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build());
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC()));
        MasterVerdicts verdicts = new MasterVerdicts(relay, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, relay));
        verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Cart.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 1, ""), acting);
        state.updateTask("ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 1));
        verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Order.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 2, ""), acting);
        state.updateTask("ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 2));

        boolean moved = verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of(new MasterReview.Said("developer", new Finding("Payment.java", "the guard is inverted", "", "blocking", "", "behaviour"))), List.of(), List.of(), 3, ""), acting);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow().loopStopped()).isFalse();
    }
}
