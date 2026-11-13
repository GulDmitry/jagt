package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestAcceptanceVerdictBuilder.acceptanceVerdict;
import static dev.jagt.orchestrator.job.TestAskedDeployBuilder.askedDeploy;
import static dev.jagt.orchestrator.job.TestCheckerOutcomesBuilder.checkerOutcomes;
import static dev.jagt.orchestrator.job.TestFlowReportsBuilder.flowReports;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.RoleRecord;
import dev.jagt.orchestrator.task.TaskState;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class AcceptanceOutcomesTest {

  @TempDir Path root;

  private StateService state;
  private CheckerOutcomes outcomes;

  @BeforeEach
  void outcomesOverAnEmptyState() {
    state = stateService().withRoot(root).build();
    outcomes =
        checkerOutcomes()
            .build(
                mock(StepRelay.class),
                flowReports().build(state, Clock.systemUTC()),
                mock(TaskStops.class),
                state);
  }

  @ParameterizedTest
  @CsvSource({"0123456789abcdef,PASSED,true", "fedcba9876543210,DROPPED,false"})
  void passesTheChangeOnlyWhileTheVerdictShaIsTheDeployTip(
      String tip, AcceptanceEnding ending, boolean passed) {
    TaskState task = askedDeploy().withMerge(tip).build();
    state.putTask("ABC-42", task);

    AcceptanceEnding result = outcomes.apply("ABC-42", task, acceptanceVerdict().build());

    assertThat(result).isEqualTo(ending);
    assertThat(state.task("ABC-42").orElseThrow().passedBy(Checkers.ACCEPTANCE)).isEqualTo(passed);
  }

  @Test
  void showsNoPassedLabelOnceLaterDeployMovedTheTip() {
    TaskState task =
        askedDeploy()
            .build()
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
  void recordsNoRoundSentWhereTheSessionWasNotHandedTheRows() {
    TaskState task = askedDeploy().build();
    state.putTask("ABC-42", task);
    AcceptanceVerdict verdict =
        acceptanceVerdict()
            .withRows(
                List.of(
                    new AcceptanceVerdict.Row(
                        "login works", AcceptanceVerdict.Status.NOT_MET, "GET /me 500")))
            .build();

    AcceptanceEnding ending = outcomes.apply("ABC-42", task, verdict);

    assertThat(ending).isEqualTo(AcceptanceEnding.NOT_MET);
    assertThat(state.task("ABC-42").orElseThrow()).isEqualTo(task);
  }

  @ParameterizedTest
  @MethodSource("whatTheHumanIsAsked")
  void asksTheHumanWhatAcceptanceCouldNotProve(
      List<AcceptanceVerdict.Row> rows, String question, String asked) {
    TaskState task = askedDeploy().build();
    state.putTask("ABC-42", task);

    outcomes.apply(
        "ABC-42", task, acceptanceVerdict().withRows(rows).withQuestion(question).build());

    assertThat(state.task("ABC-42").orElseThrow().message()).endsWith(asked);
  }

  static Stream<Arguments> whatTheHumanIsAsked() {
    AcceptanceVerdict.Row unprovable =
        new AcceptanceVerdict.Row("login works", AcceptanceVerdict.Status.UNPROVABLE, "no login");
    AcceptanceVerdict.Row met =
        new AcceptanceVerdict.Row("page opens", AcceptanceVerdict.Status.MET, "200");
    return Stream.of(
        Arguments.of(List.of(unprovable), "which user?", ":\nwhich user?"),
        Arguments.of(
            List.of(met, unprovable),
            "",
            ":\n- login works — unprovable: no login"),
        Arguments.of(List.of(), "", ":\nthe check named no criterion"));
  }
}
