package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ApprovedPlan;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.MissLog;
import dev.jagt.orchestrator.service.PlanApproval;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.StopPolicy;
import dev.jagt.orchestrator.service.TaskMovedOn;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.Unplaceable;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class MasterPlanVerdictsTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final MasterDecisions decisions = spy(new MasterDecisions(mock(WorktreeChanges.class)));

  @Test
  void relaysNothingOfPlanTheHumanMovedWhileTheMasterRead(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState read = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    state.putTask("ABC-1", read);
    state.updateTask(
        "ABC-1",
        live ->
            live.withStatus(TaskStatus.IN_PROGRESS, null, 6_000)
                .withTurn(new Turn("plan", 6, "d", 6_000, Map.of(), List.of())));
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, mock(ApprovedPlan.class)));

    assertThatThrownBy(
            () ->
                verdicts.holds(
                    "ABC-1",
                    read,
                    new MasterReview.Verdict(
                        MasterReview.Kind.READY, List.of(), List.of(), List.of(), 0, ""),
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null)))
        .isInstanceOf(TaskMovedOn.class);

    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void startsTheSessionOnPlanTheMasterFoundHolds(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    state.putTask("ABC-1", planned);
    var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, mock(ApprovedPlan.class)));

    boolean moved =
        verdicts.holds(
            "ABC-1",
            planned,
            new MasterReview.Verdict(
                MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
            acting);

    assertThat(moved).isTrue();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.IN_PROGRESS, TaskState::status)
        .returns("master: the plan holds", TaskState::message);
  }

  @Test
  void returnsToTheSessionPlanNamingProjectJagtLacksInsteadOfAskingTheHuman(@TempDir Path root)
      throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState planned =
        TaskState.builder(List.of(TaskRepo.of(null, root.toString())), TaskStatus.PLAN_PENDING)
            .alias("a1")
            .build();
    state.putTask("ABC-1", planned);
    var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    ApprovedPlan plan = mock(ApprovedPlan.class);
    when(plan.settle("ABC-1")).thenThrow(new Unplaceable(List.of("billing"), List.of("api")));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, plan));

    boolean moved =
        verdicts.holds(
            "ABC-1",
            planned,
            new MasterReview.Verdict(
                MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
            acting);

    assertThat(moved).isTrue();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(AgentReport.PLAIN, TaskState::report)
        .returns(true, TaskState::unplaced);
    verify(sessions)
        .writeTaskContext(
            eq("ABC-1"), contains("billing, which jagt cannot place; registered: api"));
  }

  @Test
  void opensTheGateOfAnAutoplannedTaskWhenTheMasterFindsThePlanHolds(@TempDir Path root)
      throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState planned =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.CLOSED)
            .build();
    state.putTask("ABC-1", planned);
    var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, mock(ApprovedPlan.class)));

    verdicts.holds(
        "ABC-1",
        planned,
        new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
        acting);

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void leavesPlanAskedForInPlanModeForTheHumanThoughTheMasterMayApprovePlans(@TempDir Path root)
      throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState planned =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build();
    state.putTask("ABC-1", planned);
    var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, mock(ApprovedPlan.class)));

    boolean moved =
        verdicts.holds(
            "ABC-1",
            planned,
            new MasterReview.Verdict(
                MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
            acting);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.PLAN_PENDING, TaskState::status)
        .returns(PlanGate.REQUIRED, TaskState::planGate);
  }

  @Test
  void leavesPlanThatHoldsForTheHumanWhoKeptThatStep(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    state.putTask("ABC-1", planned);
    var kept = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("plan"), null);
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, mock(ApprovedPlan.class)));

    boolean moved =
        verdicts.holds(
            "ABC-1",
            planned,
            new MasterReview.Verdict(
                MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
            kept);

    assertThat(moved).isFalse();
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
  }

  @Test
  void answersThePlansQuestionWithTheDecisionBeforeStartingTheSession(@TempDir Path root)
      throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState planned =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .message("admins only or every user?")
            .report(AgentReport.QUESTION)
            .build();
    state.putTask("ABC-1", planned);
    var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, mock(ApprovedPlan.class)));

    verdicts.holds(
        "ABC-1",
        planned,
        new MasterReview.Verdict(
            MasterReview.Kind.READY,
            List.of(),
            List.of(
                new MasterReview.Said(
                    "planner",
                    new Finding(
                        "",
                        "the endpoint is for admins only, as the ticket says",
                        "decided",
                        "noise",
                        "",
                        "scope"))),
            List.of(),
            1,
            ""),
        acting);

    verify(sessions).writeTaskContext(eq("ABC-1"), contains("answered your question"));
    verify(sessions)
        .writeTaskContext(
            eq("ABC-1"), contains("the endpoint is for admins only, as the ticket says"));
    verify(sessions).writeTaskContext(eq("ABC-1"), contains("the plan holds."));
  }

  @Test
  void startsNothingOnPlanTheSessionWasAlreadyToldHolds(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    state.putTask("ABC-1", planned);
    var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, mock(ApprovedPlan.class)));

    boolean moved =
        verdicts.holds(
            "ABC-1",
            planned,
            new MasterReview.Verdict(
                MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
            acting);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.PLAN_PENDING);
  }

  @Test
  void startsNoSessionOnPlanWhereNoMasterCaseAllowsTheApproval(@TempDir Path root)
      throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    state.putTask("ABC-1", planned);
    var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, mock(ApprovedPlan.class)));

    boolean moved =
        verdicts.holds(
            "ABC-1",
            planned,
            new MasterReview.Verdict(
                MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, ""),
            acting);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.PLAN_PENDING);
  }

  @Test
  void movesNothingOfPlanThatIsGoneFromTheState(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).alias("a1").build();
    var acting = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    MasterPlanVerdicts verdicts =
        new MasterPlanVerdicts(
            relay,
            new MasterClaims(
                new FlowReports(
                    state,
                    Clock.systemUTC(),
                    new Checkers(new TaskChart(Composition.builtIn())),
                    new FlowRules(new TaskChart(Composition.builtIn())))),
            decisions,
            new PlanApproval(state, relay, door, mock(ApprovedPlan.class)));

    boolean moved =
        verdicts.holds(
            "ABC-1",
            task,
            new MasterReview.Verdict(
                MasterReview.Kind.READY,
                List.of(
                    new MasterReview.Said(
                        "developer",
                        new Finding("Foo.java", "wrong", "", "blocking", "", "behaviour"))),
                List.of(),
                List.of(),
                1,
                ""),
            acting);

    assertThat(moved).isFalse();
  }
}
