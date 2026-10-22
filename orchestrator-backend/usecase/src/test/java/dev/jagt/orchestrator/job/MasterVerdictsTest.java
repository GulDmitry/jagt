package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.PlanApproval;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterVerdictsTest {

    private final AgentSessions sessions = mock(AgentSessions.class);
    private final MasterShip ship = mock(MasterShip.class);
    private final MasterDecisions decisions = mock(MasterDecisions.class);

    @Test
    void sendsARoundThatIsNotReadyBackToWhoeverWroteTheCode(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("Foo.java:12 the guard is inverted"), 1, ""), acting);

        assertThat(moved).isFalse();
        verify(sessions).relayIfChanged(eq("ABC-1"), contains("the guard is inverted"));
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void takesANotReadyRoundBackToWorkSoTheFixedOneIsReadAgain(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("Foo.java:12 the guard is inverted"), 1, ""), acting);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void putsTheSessionBackToWorkOnTheAnswerTheMasterGaveIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(sessions.relayIfChanged(eq("ABC-1"), contains("keep v2 beside v3"))).thenReturn(true);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.answered("ABC-1", task, "keep v2?", "keep v2 beside v3", false);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow())
                .returns(TaskStatus.IN_PROGRESS, TaskState::status)
                .returns("master answered the question", TaskState::message);
    }

    @Test
    void keepsTheDeployConflictHandOffStandingUnderTheAnswerToAQuestion(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.DEPLOY_CONFLICT).alias("a1").build();
        state.putTask("ABC-42", task);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.answered("ABC-42", task, "keep both imports?", "keep both imports", false);

        verify(sessions).relayUnder(eq("ABC-42"), contains("keep both imports"));
        verify(sessions, never()).relayIfChanged(eq("ABC-42"), anyString());
    }

    @Test
    void opensATaskForWorkTheMasterMovedToABranchOfItsOwn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(ship.open("proj drop the old keys from main")).thenReturn("drop-the-old-keys started");
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.answered("ABC-1", task, "a second request?", "do proj drop the old keys from main", false);

        verify(sessions).relayIfChanged(eq("ABC-1"), contains("drop-the-old-keys started"));
    }

    @Test
    void opensNoTaskWhereTheMasterAnswersAQuestionItAskedItself(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).alias("a1").build();
        state.putTask("ABC-1", task);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.answered("ABC-1", task, "outcome=question — reviewer: it also drops the cache; own task?",
                "do proj drop the cache from main", false);

        verify(ship, never()).open(anyString());
    }

    @Test
    void restartsTheSessionWithFreshToolsWhenItsAnswersChangedNothing(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(sessions.relayIfChanged(eq("ABC-1"), contains("refactor the shared block"))).thenReturn(true);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.answered("ABC-1", task, "override the gate?", "refactor the shared block", true);

        verify(sessions).openTaskTab("ABC-1", null);
    }

    @Test
    void settlesWhatANotReadyRoundSentBackSoTheNextRoundDoesNotReopenIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("Foo.java:12 the guard is inverted"), 1, ""), acting);

        verify(decisions).record(task, "Foo.java:12 the guard is inverted");
    }

    @Test
    void relaysAFindingAnEarlierRoundMadeMarkedAsARepeat(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(decisions.repeats(task, "- [qa] Foo.java — the delay is never pinned (unguarded delay)"))
                .thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("- [qa] Foo.java — the delay is never pinned (unguarded delay)"), 7, ""), acting);

        verify(sessions).relayIfChanged(eq("ABC-1"),
                contains("- [qa] Foo.java — the delay is never pinned (unguarded delay) [repeat]"));
    }

    @Test
    void asksTheHumanWhenRoundsOfRepeatsKeepComingBack(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(decisions.repeats(task, "- [qa] Foo.java — the delay is never pinned (unguarded delay)"))
                .thenReturn(true);
        when(decisions.returned(task, 7, true)).thenReturn(2);
        when(sessions.relayIfChanged(eq("ABC-1"), contains("The reviewer asked the human"))).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("- [qa] Foo.java — the delay is never pinned (unguarded delay)"), 7, ""), acting);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow().message()).isEqualTo("outcome=question — reviewer: These findings"
                + " came back 2 rounds running, neither fixed nor disputed:"
                + " - [qa] Foo.java — the delay is never pinned (unguarded delay) [repeat]");
    }

    @Test
    void settlesNoRequestForEvidenceSoTheSessionsAnswerCanStillChangeIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("[chaplain] Api.java — show: which caller still reads v2",
                        "[developer] Foo.java:12 — the guard is inverted"), 1, ""), acting);

        verify(decisions).record(task, "[developer] Foo.java:12 — the guard is inverted");
    }

    @Test
    void shipsAReadyRoundOnlyWhereAHumanSaidTheReviewerStandsInForThem(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(ship.ship("ABC-1", task)).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1, ""),
                acting);

        assertThat(moved).isTrue();
        verify(ship).ship("ABC-1", task);
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void shipsNothingWhereTheHumanKeptThatStep(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var kept = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("ship"), null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1, ""),
                kept);

        assertThat(moved).isFalse();
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void leavesAReadyRoundForTheHumanWhileTheReviewerOnlyJudges(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var judging = new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1, ""),
                judging);

        assertThat(moved).isFalse();
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void startsTheSessionOnAPlanTheMasterFoundHolds(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
        state.putTask("ABC-1", planned);
        when(sessions.relayIfChanged(eq("ABC-1"), contains("the plan holds. Start on it."))).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1, ""),
                acting);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow())
                .returns(TaskStatus.IN_PROGRESS, TaskState::status)
                .returns("master: the plan holds", TaskState::message);
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void opensTheGateOfAnAutoplannedTaskWhenTheMasterFindsThePlanHolds(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.CLOSED).build();
        state.putTask("ABC-1", planned);
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1, ""), acting);

        assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.OPEN);
    }

    @Test
    void leavesAPlanAskedForInPlanModeForTheHumanThoughTheMasterMayApprovePlans(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.REQUIRED).build();
        state.putTask("ABC-1", planned);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", planned,
                new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1, ""), acting);

        assertThat(moved).isFalse();
        assertThat(state.task("ABC-1").orElseThrow())
                .returns(TaskStatus.PLAN_PENDING, TaskState::status)
                .returns(PlanGate.REQUIRED, TaskState::planGate);
    }

    @Test
    void keepsThePlanGateClosedWhenTheMasterSendsThePlanBack(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.REQUIRED).build();
        state.putTask("ABC-1", planned);
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("plan.md misses the migration"), 1, ""), acting);

        assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
    }

    @Test
    void leavesAPlanThatHoldsForTheHumanWhoKeptThatStep(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
        state.putTask("ABC-1", planned);
        var kept = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("plan"), null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1, ""),
                kept);

        assertThat(moved).isFalse();
        verify(sessions, never()).relayIfChanged(anyString(), anyString());
    }

    @Test
    void sendsAPlanThatMissesTheTicketBackToBeReworked(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
        state.putTask("ABC-1", planned);
        var judging = new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("[planner] plan.md — drops the v2 route the ticket keeps"), 1, ""), judging);

        verify(sessions).relayIfChanged(eq("ABC-1"), contains("Then report PLAN_PENDING again.\n\n"
                + "[planner] plan.md — drops the v2 route the ticket keeps"));
    }

    @Test
    void putsTheReviewersQuestionToTheHumanThroughTheSessionThatWaitsOnTheAnswer(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(sessions.relayIfChanged(eq("ABC-1"), contains("Add v3 beside v2, or replace it?"))).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.QUESTION,
                List.of("Foo.java drops v2", "Add v3 beside v2, or replace it?"), 1, ""), acting);

        assertThat(moved).isTrue();
        assertThat(state.task("ABC-1").orElseThrow())
                .returns(TaskStatus.IN_PROGRESS, TaskState::status)
                .returns("outcome=question — reviewer: Add v3 beside v2, or replace it?", TaskState::message);
        verify(ship, never()).ship(anyString(), any());
    }

    @Test
    void asksTheSameQuestionOnce(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.QUESTION,
                List.of("Add v3 beside v2, or replace it?"), 1, ""), acting);

        assertThat(moved).isFalse();
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void asksTheHumanNothingWhenTheSessionAlreadyHoldsTheStalledRoundsQuestion(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(decisions.repeats(task, "- [qa] Foo.java — the delay is never pinned")).thenReturn(true);
        when(decisions.returned(task, 7, true)).thenReturn(2);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("- [qa] Foo.java — the delay is never pinned"), 7, ""), acting);

        assertThat(moved).isFalse();
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void countsARoundAsRepeatedOnlyWhenEveryFindingRepeatsAnEarlierOne(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(decisions.repeats(task, "- [qa] Foo.java — the delay is never pinned")).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("- [qa] Foo.java — the delay is never pinned", "- [developer] Bar.java:3 — a new finding"), 7, ""),
                acting);

        verify(decisions).returned(task, 7, false);
    }

    @Test
    void recordsWhatTheMasterDecidedSoALaterRoundDoesNotReopenIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.answered("ABC-1", task, "keep v2?", "keep v2\nbeside v3", false);

        verify(decisions).record(task, "- asked: keep v2? — decided: keep v2 beside v3");
        verify(decisions).answered(task);
    }

    @Test
    void answersNothingAndRecordsNothingWhenTheSessionAlreadyHoldsThatAnswer(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.answered("ABC-1", task, "keep v2?", "keep v2 beside v3", false);

        assertThat(moved).isFalse();
        verify(decisions, never()).record(any(), anyString());
    }

    @Test
    void opensATaskOnlyForTheLinesOfTheAnswerThatAskForOne(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        verdicts.answered("ABC-1", task, "a second request?", "keep v2 beside v3\ndo proj drop the old keys", false);

        verify(ship, times(1)).open(anyString());
    }

    @Test
    void reportsAnAnswerCountOverTheTreeAsTheDecisionsHoldIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        when(decisions.answersOverThisTree(task)).thenReturn(3);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        assertThat(verdicts.answersOverThisTree(task)).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"1,false", "2,true", "3,true"})
    void callsAReviewStalledFromTheSecondRepeatedRoundOn(int repeated, boolean stalled, @TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        when(decisions.repeatedRounds(task)).thenReturn(repeated);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        assertThat(verdicts.stalled(task)).isEqualTo(stalled);
    }

    @ParameterizedTest
    @CsvSource({"NOT_READY,REVIEW_PENDING", "QUESTION,REVIEW_PENDING", "READY,PLAN_PENDING"})
    void movesNothingOfATaskThatIsGoneFromTheState(MasterReview.Kind kind, TaskStatus status, @TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), status).alias("a1").build();
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(kind, List.of("Foo.java:12 wrong"), 1, ""),
                acting);

        assertThat(moved).isFalse();
    }

    @Test
    void movesNothingOfAnAnsweredTaskThatIsGoneFromTheState(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.answered("ABC-1", task, "keep v2?", "keep v2 beside v3", false);

        assertThat(moved).isFalse();
    }

    @Test
    void movesNothingWhenTheShipWasNotOpened(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        state.putTask("ABC-1", task);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1, ""),
                acting);

        assertThat(moved).isFalse();
    }

    @Test
    void startsNothingOnAPlanTheSessionWasAlreadyToldHolds(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
        state.putTask("ABC-1", planned);
        var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
        MasterVerdicts verdicts = new MasterVerdicts(sessions, ship, new FlowReports(state, Clock.systemUTC()), decisions,
                new PlanApproval(state, sessions));

        boolean moved = verdicts.act("ABC-1", planned, new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1, ""),
                acting);

        assertThat(moved).isFalse();
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.PLAN_PENDING);
    }
}
