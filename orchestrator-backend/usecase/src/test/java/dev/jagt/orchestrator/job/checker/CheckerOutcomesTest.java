package dev.jagt.orchestrator.job.checker;

import static dev.jagt.orchestrator.job.TestAcceptanceVerdictBuilder.acceptanceVerdict;
import static dev.jagt.orchestrator.job.TestAskedDeployBuilder.askedDeploy;
import static dev.jagt.orchestrator.job.TestFlowReportsBuilder.flowReports;
import static dev.jagt.orchestrator.job.checker.TestCheckerOutcomesBuilder.checkerOutcomes;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.acceptance.AcceptanceEnding;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.RoleRecord;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class CheckerOutcomesTest {

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
                flowReports().build(state, new FakeClock()),
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

    assertThat(state.task("ABC-42").orElseThrow().standing().message()).endsWith(asked);
  }

  static Stream<Arguments> whatTheHumanIsAsked() {
    AcceptanceVerdict.Row unprovable =
        new AcceptanceVerdict.Row("login works", AcceptanceVerdict.Status.UNPROVABLE, "no login");
    AcceptanceVerdict.Row met =
        new AcceptanceVerdict.Row("page opens", AcceptanceVerdict.Status.MET, "200");
    return Stream.of(
        Arguments.of(List.of(unprovable), "which user?", ":\nwhich user?"),
        Arguments.of(List.of(met, unprovable), "", ":\n- login works — unprovable: no login"),
        Arguments.of(List.of(), "", ":\nthe check named no criterion"));
  }

  @ParameterizedTest
  @CsvSource({"audit", "acceptance"})
  void sendsTheNotMetRoundUnderTheCheckerThatFoundIt(String id) throws Exception {
    Path roleFile = root.resolve("role.yml");
    Files.writeString(
        roleFile,
        Files.readString(Path.of("../../roles/acceptance/role.yml.dist"))
            .replace("id: acceptance", "id: " + id));
    StepRelay relay = mock(StepRelay.class);
    List<String> sentUnder = new ArrayList<>();
    when(relay.relay(
            eq("ABC-42"),
            eq(Step.ACCEPTANCE_ROUND),
            anyString(),
            eq(StepRelay.Delivery.ROUND),
            any(StepRelay.Claim.class)))
        .thenAnswer(call -> {
          sentUnder.add(OriginContext.current() + "/" + OriginContext.checker());
          return false;
        });
    CheckerOutcomes checker =
        checkerOutcomes()
            .withRoleFile(roleFile)
            .build(
                relay, flowReports().build(state, new FakeClock()), mock(TaskStops.class), state);
    TaskState task = askedDeploy().build();
    state.putTask("ABC-42", task);
    AcceptanceVerdict verdict =
        acceptanceVerdict()
            .withRows(
                List.of(
                    new AcceptanceVerdict.Row(
                        "login works", AcceptanceVerdict.Status.NOT_MET, "GET /me 500")))
            .build();

    checker.apply("ABC-42", task, verdict);

    assertThat(sentUnder).containsExactly("ACCEPTANCE/" + id);
  }

  @Test
  void stopsOnTheReadEndedStopNamingTheMissingInputNeverOnFailedCheck() {
    TaskStops stops = mock(TaskStops.class);
    CheckerOutcomes checker =
        checkerOutcomes()
            .build(
                mock(StepRelay.class), flowReports().build(state, new FakeClock()), stops, state);

    checker.unread("ABC-42", "ticket");

    verify(stops).stop("ABC-42", TaskStop.Trigger.ACCEPTANCE_UNREAD, "no ticket to check against");
    verify(stops, never()).stop(any(), eq(TaskStop.Trigger.ACCEPTANCE_FAILED), any());
  }

  @Test
  void endsTheStopOnTheReadThatFindsTheInputs() {
    TaskStops stops = mock(TaskStops.class);
    CheckerOutcomes checker =
        checkerOutcomes()
            .build(
                mock(StepRelay.class), flowReports().build(state, new FakeClock()), stops, state);

    checker.inputsRead("ABC-42");

    verify(stops).endOnInputRead("ABC-42");
  }
}
