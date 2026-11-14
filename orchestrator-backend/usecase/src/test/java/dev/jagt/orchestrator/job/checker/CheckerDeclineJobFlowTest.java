package dev.jagt.orchestrator.job.checker;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.AcceptanceDecline;
import dev.jagt.orchestrator.task.acceptance.AcceptanceEnding;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckerDeclineJobFlowTest {

  @TempDir Path root;
  private StateService state;
  private CheckerOutcomes outcomes;

  @BeforeEach
  void deployedTaskAndOutcomesOverIt() {
    state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .alias("a1")
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest")));
    outcomes =
        new CheckerOutcomes(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            mock(StepRelay.class),
            new FlowReports(
                state,
                Clock.systemUTC(),
                new Checkers(new TaskChart(Composition.builtIn())),
                new FlowRules(new TaskChart(Composition.builtIn()))),
            mock(TaskStops.class),
            state);
  }

  @Test
  void endsTheCheckDeclinedAndAsksNobodyWhereAcceptanceHasNoDispute() {
    AcceptanceEnding ending =
        outcomes.declined("ABC-42", new AcceptanceDecline("proj@0123456789abcdef", "", ""), false);

    assertThat(ending).isEqualTo(AcceptanceEnding.DECLINED);
    assertThat(state.task("ABC-42").orElseThrow().report()).isEqualTo(AgentReport.PLAIN);
  }

  @Test
  void putsFirstDisputeAsQuestionWhichTheMasterInActAnswersBeforeTheHuman() {
    AcceptanceEnding ending =
        outcomes.declined(
            "ABC-42",
            new AcceptanceDecline("proj@0123456789abcdef", "", "GET /list still unsorted"),
            false);

    TaskState asked = state.task("ABC-42").orElseThrow();
    assertThat(ending).isEqualTo(AcceptanceEnding.DISPUTED);
    assertThat(asked.report()).isEqualTo(AgentReport.QUESTION);
    assertThat(asked.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(asked.message()).contains("GET /list still unsorted");
  }

  @Test
  void putsSecondDisputeWhereOnlyTheHumanAnswersIt() {
    AcceptanceEnding ending =
        outcomes.declined(
            "ABC-42",
            new AcceptanceDecline("proj@0123456789abcdef", "", "still unsorted after the answer"),
            true);

    assertThat(ending).isEqualTo(AcceptanceEnding.ESCALATED);
    assertThat(state.task("ABC-42").orElseThrow().report()).isEqualTo(AgentReport.PERMISSION);
  }

  @Test
  void dropsDisputeOnShaThatIsNoLongerTheDeployTip() {
    AcceptanceEnding ending =
        outcomes.declined("ABC-42", new AcceptanceDecline("proj@fedcba9876543210", "", "x"), false);

    assertThat(ending).isEqualTo(AcceptanceEnding.DROPPED);
    assertThat(state.task("ABC-42").orElseThrow().report()).isEqualTo(AgentReport.PLAIN);
  }
}
