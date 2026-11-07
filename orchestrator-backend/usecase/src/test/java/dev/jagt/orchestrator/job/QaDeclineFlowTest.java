package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class QaDeclineFlowTest {

  private static TaskState deployed() {
    return TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
        .alias("a1")
        .build()
        .withDeployment(
            new Deployment(
                1,
                List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                1,
                "digest"));
  }

  @Test
  void endsTheCheckDeclinedAndAsksNobodyWhereQaHasNoDispute(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask("ABC-42", deployed());

    QaEnding ending =
        new CheckerOutcomes(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
                mock(StepRelay.class),
                new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class),
                state)
            .declined("ABC-42", new QaDecline("proj@0123456789abcdef", "", ""), false);

    assertThat(ending).isEqualTo(QaEnding.DECLINED);
    assertThat(state.task("ABC-42").orElseThrow().report()).isEqualTo(AgentReport.PLAIN);
  }

  @Test
  void putsAFirstDisputeAsAQuestionWhichTheMasterInActAnswersBeforeTheHuman(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask("ABC-42", deployed());

    QaEnding ending =
        new CheckerOutcomes(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
                mock(StepRelay.class),
                new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class),
                state)
            .declined(
                "ABC-42",
                new QaDecline("proj@0123456789abcdef", "", "GET /list still unsorted"),
                false);

    TaskState asked = state.task("ABC-42").orElseThrow();
    assertThat(ending).isEqualTo(QaEnding.DISPUTED);
    assertThat(asked.report()).isEqualTo(AgentReport.QUESTION);
    assertThat(asked.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(asked.message()).contains("GET /list still unsorted");
  }

  @Test
  void putsASecondDisputeWhereOnlyTheHumanAnswersIt(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask("ABC-42", deployed());

    QaEnding ending =
        new CheckerOutcomes(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
                mock(StepRelay.class),
                new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class),
                state)
            .declined(
                "ABC-42",
                new QaDecline("proj@0123456789abcdef", "", "still unsorted after the answer"),
                true);

    assertThat(ending).isEqualTo(QaEnding.ESCALATED);
    assertThat(state.task("ABC-42").orElseThrow().report()).isEqualTo(AgentReport.PERMISSION);
  }

  @Test
  void dropsADisputeOnAShaThatIsNoLongerTheDeployTip(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask("ABC-42", deployed());

    QaEnding ending =
        new CheckerOutcomes(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
                mock(StepRelay.class),
                new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class),
                state)
            .declined("ABC-42", new QaDecline("proj@fedcba9876543210", "", "x"), false);

    assertThat(ending).isEqualTo(QaEnding.DROPPED);
    assertThat(state.task("ABC-42").orElseThrow().report()).isEqualTo(AgentReport.PLAIN);
  }
}
