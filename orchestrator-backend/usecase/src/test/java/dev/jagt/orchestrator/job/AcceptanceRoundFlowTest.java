package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestAcceptanceVerdictBuilder.acceptanceVerdict;
import static dev.jagt.orchestrator.job.TestAgentStatusReportsBuilder.agentStatusReports;
import static dev.jagt.orchestrator.job.TestAskedDeployBuilder.askedDeploy;
import static dev.jagt.orchestrator.job.TestCheckerOutcomesBuilder.checkerOutcomes;
import static dev.jagt.orchestrator.job.TestFlowReportsBuilder.flowReports;
import static dev.jagt.orchestrator.job.TestRolesRootBuilder.rolesRoot;
import static dev.jagt.orchestrator.job.TestStepRelayBuilder.stepRelay;
import static dev.jagt.orchestrator.job.TestTaskTurnsBuilder.taskTurns;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceRoundFlowTest {

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final ConfigService config = mock(ConfigService.class);

  @TempDir Path root;

  private StateService state;
  private FlowReports flow;
  private TaskTurns turns;
  private TaskStops stops;
  private AcceptanceVerdict notMet;
  private StepRelay relay;
  private AgentStatusReports reports;
  private TaskState task;

  @BeforeEach
  void deployedTaskWaitingOnAcceptance() throws IOException {
    rolesRoot().withRoot(root).build();
    state = stateService().withRoot(root).build();
    flow = flowReports().build(state, Clock.systemUTC());
    turns = taskTurns().build(state, Clock.systemUTC());
    stops = taskStops().withRoot(root).build(state, Clock.systemUTC());
    notMet =
        acceptanceVerdict()
            .withRows(
                List.of(
                    new AcceptanceVerdict.Row(
                        "login works", AcceptanceVerdict.Status.NOT_MET, "GET /me answered 500")))
            .build();
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    relay = stepRelay().withRoot(root).build(state, sessions, Clock.systemUTC());
    reports = agentStatusReports().build(state, flow, mock(WorktreeChanges.class), config);
    task = askedDeploy().withWorktree(root.toString()).withAlias("a1").build();
  }

  @Test
  void movesDeployedTaskToInProgressAndHoldsItThereUntilTheSessionReportsNoChanges() {
    state.putTask("ABC-42", task);

    checkerOutcomes().build(relay, flow, stops, state).apply("ABC-42", task, notMet);
    TaskState relayed = state.task("ABC-42").orElseThrow();
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "the endpoint is fine", "no_changes", null, Map.of()),
        "ABC-42");

    assertThat(relayed.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    TaskState answered = state.task("ABC-42").orElseThrow();
    assertThat(answered.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(answered.report()).isEqualTo(AgentReport.NO_CHANGES);
  }

  @Test
  void holdsDeployedWhereTheSessionDeclinesRoundAfterItsQuestionWasAnsweredInsideTheRound() {
    state.putTask("ABC-42", task);

    checkerOutcomes().build(relay, flow, stops, state).apply("ABC-42", task, notMet);
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "which environment?", "question", null, Map.of()),
        "ABC-42");
    turns.opened("ABC-42", "answered", "d2", TaskStatus.IN_PROGRESS);
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "the endpoint is fine", "no_changes", null, Map.of()),
        "ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
  }

  @Test
  void asksTheHumanThroughTheFlowWhereRowIsUnprovableAndKeepsTheStatusDeployed() {
    state.putTask("ABC-42", task);
    AcceptanceVerdict unprovable =
        acceptanceVerdict()
            .withRows(
                List.of(
                    new AcceptanceVerdict.Row(
                        "login works", AcceptanceVerdict.Status.UNPROVABLE, "no login")))
            .build();

    checkerOutcomes()
        .build(mock(StepRelay.class), flow, mock(TaskStops.class), state)
        .apply("ABC-42", task, unprovable);

    TaskState asked = state.task("ABC-42").orElseThrow();
    assertThat(asked.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(asked.report()).isEqualTo(AgentReport.QUESTION);
    assertThat(asked.message()).contains("Acceptance could not prove");
  }

  @Test
  void refusesVerdictAtTheDoorWhereTheTaskWasClosedWhileAcceptanceRead() {
    StepRelay unused = mock(StepRelay.class);

    AcceptanceEnding ending =
        checkerOutcomes()
            .build(unused, flow, mock(TaskStops.class), state)
            .apply("ABC-42", task, notMet);

    assertThat(ending).isEqualTo(AcceptanceEnding.GONE);
    verifyNoInteractions(unused);
  }
}
