package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class OwnStatusReportsTest {

  private static final String DEPLOY_CASE =
      """
      ## deploy

      step: deploy
      at: DEPLOYED
      by: jagt
      replies: IN_PROGRESS; plain, question
      The task is live.
      """;

  private final AgentStatusReports statusReports = mock(AgentStatusReports.class);
  private final HandBackDue due = mock(HandBackDue.class);
  private final CaseDoor door = mock(CaseDoor.class);
  private final Checkers checkers = new Checkers(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  private StateService state;
  private OwnStatusReports doorReports;
  private CaseDoor realDoor;

  @BeforeEach
  void wireDoor() {
    state = stateService().withRoot(root).build();
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    realDoor =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(
                new JsonMapper(), paths, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)),
            taskStops().withRoot(root).build(state, Clock.systemUTC()));
    doorReports = new OwnStatusReports(state, due, statusReports, realDoor, effects(state));
    state.putTask(
        "ABC-42", TaskState.builder("proj", root.toString(), TaskStatus.DEPLOYED).build());
    new TaskTurns(state, Clock.systemUTC(), checkers)
        .opened("ABC-42", "deploy", "a1b2", TaskStatus.REVIEWED);
    when(statusReports.contextFor("ABC-42")).thenReturn(MessageContext.NONE);
  }

  private static ReportEffects effects(StateService state) {
    return effects(state, (task, line) -> "");
  }

  private static ReportEffects effects(StateService state, SessionLines sessions) {
    TaskTurns turns =
        new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())));
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(
                    new StopLog(
                        new JsonMapper(),
                        new OrchestratorPaths(OrchestratorProperties.defaults())))),
            state,
            Clock.systemUTC(),
            new FlowRules(new TaskChart(Composition.builtIn())));
    return new ReportEffects(
        new SessionWakes(state, turns, Clock.systemUTC()),
        turns,
        stops,
        InfraWaitProperties.defaults(),
        new LinkRefusals(new ReachLedger(), sessions, state));
  }

  @Test
  void refusesSessionsHandBackWhatTheWorktreeSaysIsOwed() {

    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
    when(due.invalid(any())).thenReturn(Optional.empty());
    when(due.owed(any(), any()))
        .thenReturn(Optional.of("write task_notes.md before handing the round back"));
    OwnStatusReports reports =
        new OwnStatusReports(state, due, statusReports, door, effects(state));
    AgentStatusMessage handBack =
        new AgentStatusMessage("REVIEW_PENDING", "done", null, null, Map.of());

    assertThatThrownBy(() -> reports.report(handBack, "ABC-1"))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE))
        .hasMessage("write task_notes.md before handing the round back");
  }

  @Test
  void asksTheSessionToFixHandBackWhoseNotesAreInvalidRatherThanRefusingItForGood() {

    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
    when(due.invalid(any())).thenReturn(Optional.of("task_notes.md names the model 'gpt'"));
    OwnStatusReports reports =
        new OwnStatusReports(state, due, statusReports, door, effects(state));
    AgentStatusMessage handBack =
        new AgentStatusMessage("REVIEW_PENDING", "done", null, null, Map.of());

    assertThatThrownBy(() -> reports.report(handBack, "ABC-1"))
        .isNotInstanceOf(Refusal.class)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("task_notes.md names the model 'gpt'");
  }

  @Test
  void tellsTheSessionItsForeignLink() {

    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .repos(List.of(new TaskRepo("proj", root.toString(), "git@host:team/proj.git", null)))
            .build());
    when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
    when(door.takes("ABC-1", TaskStatus.CI_POLLING, AgentReport.PLAIN)).thenReturn(true);
    List<String> said = new ArrayList<>();
    OwnStatusReports reports =
        new OwnStatusReports(
            state,
            due,
            statusReports,
            door,
            effects(
                state,
                (task, line) -> {
                  said.add(line);
                  return "";
                }));

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING", "shipped", null, "https://host/other/proj/-/merge_requests/9", Map.of()),
        "ABC-1");

    assertThat(said).singleElement().asString().contains("team/proj");
  }

  @Test
  void asksNoNotesOfReportTheMasterDoesNotRead() {

    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
    AgentStatusMessage working =
        new AgentStatusMessage("IN_PROGRESS", "still going", null, null, Map.of());
    when(statusReports.report(working, "ABC-1")).thenReturn("Task ABC-1 -> IN_PROGRESS");
    when(door.takes("ABC-1", TaskStatus.IN_PROGRESS, AgentReport.PLAIN)).thenReturn(true);
    OwnStatusReports reports =
        new OwnStatusReports(state, due, statusReports, door, effects(state));

    reports.report(working, "ABC-1");

    verifyNoInteractions(due);
  }

  @Test
  void passesPlanThroughWithoutAskingForNotes() {

    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(statusReports.contextFor("ABC-1")).thenReturn(MessageContext.NONE);
    AgentStatusMessage plan =
        new AgentStatusMessage("PLAN_PENDING", "plan ready", null, null, Map.of());
    when(statusReports.report(plan, "ABC-1")).thenReturn("Task ABC-1 -> PLAN_PENDING");
    when(door.takes("ABC-1", TaskStatus.PLAN_PENDING, AgentReport.PLAIN)).thenReturn(true);
    OwnStatusReports reports =
        new OwnStatusReports(state, due, statusReports, door, effects(state));

    String answer = reports.report(plan, "ABC-1");

    assertThat(answer).isEqualTo("Task ABC-1 -> PLAN_PENDING");
  }

  @Test
  void appliesReplyCaseOfTheOpenTurnsStepTakes() throws Exception {

    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/cases.md"), DEPLOY_CASE);
    AgentStatusMessage broke =
        new AgentStatusMessage("IN_PROGRESS", "the page 500s", null, null, Map.of());

    doorReports.report(broke, "ABC-42");

    verify(statusReports).report(broke, "ABC-42");
  }

  @Test
  void refusesReplyNoCaseOfTheOpenTurnsStepTakesAndAppliesNothing() throws Exception {

    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/cases.md"), DEPLOY_CASE);
    AgentStatusMessage handBack =
        new AgentStatusMessage("REVIEW_PENDING", "fixed", null, null, Map.of());

    assertThatThrownBy(() -> doorReports.report(handBack, "ABC-42"))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
    verify(statusReports, never()).report(handBack, "ABC-42");
  }

  @Test
  void recordsRefusedReplyWithTheStepWhatCameAndWhatTheCasesTake() throws Exception {

    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/cases.md"), DEPLOY_CASE);
    AgentStatusMessage handBack =
        new AgentStatusMessage("REVIEW_PENDING", "fixed", null, null, Map.of());

    assertThatThrownBy(() -> doorReports.report(handBack, "ABC-42")).isInstanceOf(Refusal.class);

    assertThat(Files.readString(root.resolve("misses.jsonl")))
        .isEqualTo(
            "{\"at\":1000,\"task\":\"ABC-42\",\"step\":\"deploy\",\"status\":\"DEPLOYED\",\"by\":"
                + "\"session\",\"got\":\"REVIEW_PENDING;"
                + " plain\",\"expected\":\"IN_PROGRESS; plain, question\"}\n");
  }

  @Test
  void stopsTheTaskForTheHumanOnReplyNoCaseTakes() throws Exception {

    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/cases.md"), DEPLOY_CASE);
    AgentStatusMessage handBack =
        new AgentStatusMessage("REVIEW_PENDING", "fixed", null, null, Map.of());

    assertThatThrownBy(() -> doorReports.report(handBack, "ABC-42")).isInstanceOf(Refusal.class);

    assertThat(Objects.requireNonNull(state.task("ABC-42").orElseThrow().stop()).trigger())
        .isEqualTo(TaskStop.Trigger.MISS);
  }
}
