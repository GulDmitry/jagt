package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.RoleRecord;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class AcceptanceOutcomesTest {

  @Test
  void marksTheChangePassedWhileItsShaIsTheDeployTip(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"));
    state.putTask("ABC-42", task);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "proj@0123456789abcdef",
            "",
            List.of(
                new AcceptanceVerdict.Row(
                    "login works", AcceptanceVerdict.Status.MET, "GET /me 200")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceEnding ending =
        new CheckerOutcomes(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                mock(StepRelay.class),
                new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class),
                state)
            .apply("ABC-42", task, verdict);

    assertThat(ending).isEqualTo(AcceptanceEnding.PASSED);
    assertThat(state.task("ABC-42").orElseThrow().passedBy(Checkers.ACCEPTANCE)).isTrue();
  }

  @Test
  void dropsAVerdictOnAShaThatIsNoLongerTheDeployTip(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("fedcba9876543210")),
                    1,
                    "digest"));
    state.putTask("ABC-42", task);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "proj@0123456789abcdef",
            "",
            List.of(
                new AcceptanceVerdict.Row(
                    "login works", AcceptanceVerdict.Status.MET, "GET /me 200")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceEnding ending =
        new CheckerOutcomes(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                mock(StepRelay.class),
                new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class),
                state)
            .apply("ABC-42", task, verdict);

    assertThat(ending).isEqualTo(AcceptanceEnding.DROPPED);
    assertThat(state.task("ABC-42").orElseThrow().passedBy(Checkers.ACCEPTANCE)).isFalse();
  }

  @Test
  void showsNoPassedLabelOnceALaterDeployMovedTheTip(@TempDir Path root) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"))
            .withRole(
                Checkers.ACCEPTANCE, RoleRecord.defaults().withPassed("proj@0123456789abcdef"));

    TaskState later =
        task.withDeployment(
            new Deployment(
                2,
                List.of(Deployment.Target.of("proj", "dev").withMerge("fedcba9876543210")),
                1,
                "next"));

    assertThat(task.passedBy(Checkers.ACCEPTANCE)).isTrue();
    assertThat(later.passedBy(Checkers.ACCEPTANCE)).isFalse();
  }

  @Test
  void recordsNoRoundSentWhereTheSessionWasNotHandedTheRows(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"));
    state.putTask("ABC-42", task);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "proj@0123456789abcdef",
            "",
            List.of(
                new AcceptanceVerdict.Row(
                    "login works", AcceptanceVerdict.Status.NOT_MET, "GET /me 500")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceEnding ending =
        new CheckerOutcomes(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                mock(StepRelay.class),
                new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class),
                state)
            .apply("ABC-42", task, verdict);

    assertThat(ending).isEqualTo(AcceptanceEnding.NOT_MET);
    assertThat(state.task("ABC-42").orElseThrow()).isEqualTo(task);
  }

  @Test
  void asksTheQuestionAcceptancePutWhereItSentOneAndNotTheRows(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"));
    state.putTask("ABC-42", task);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "proj@0123456789abcdef",
            "",
            List.of(
                new AcceptanceVerdict.Row(
                    "login works", AcceptanceVerdict.Status.UNPROVABLE, "no login")),
            "which user?",
            "",
            AcceptanceVerdict.Flows.none());

    new CheckerOutcomes(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            mock(StepRelay.class),
            new FlowReports(state, Clock.systemUTC()),
            mock(TaskStops.class),
            state)
        .apply("ABC-42", task, verdict);

    assertThat(state.task("ABC-42").orElseThrow().message()).endsWith(":\nwhich user?");
  }

  @Test
  void asksOnlyTheRowsNotMetWhereAcceptanceSentNoQuestion(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"));
    state.putTask("ABC-42", task);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "proj@0123456789abcdef",
            "",
            List.of(
                new AcceptanceVerdict.Row("page opens", AcceptanceVerdict.Status.MET, "200"),
                new AcceptanceVerdict.Row(
                    "login works", AcceptanceVerdict.Status.UNPROVABLE, "no login")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    new CheckerOutcomes(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            mock(StepRelay.class),
            new FlowReports(state, Clock.systemUTC()),
            mock(TaskStops.class),
            state)
        .apply("ABC-42", task, verdict);

    assertThat(state.task("ABC-42").orElseThrow().message())
        .endsWith(":\n- login works — unprovable: no login");
  }

  @Test
  void namesNoCriterionWhereAcceptanceProvedNothingAndSentNoRowOrQuestion(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"));
    state.putTask("ABC-42", task);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "proj@0123456789abcdef", "", List.of(), "", "", AcceptanceVerdict.Flows.none());

    new CheckerOutcomes(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            mock(StepRelay.class),
            new FlowReports(state, Clock.systemUTC()),
            mock(TaskStops.class),
            state)
        .apply("ABC-42", task, verdict);

    assertThat(state.task("ABC-42").orElseThrow().message())
        .endsWith(":\nthe check named no criterion");
  }
}
