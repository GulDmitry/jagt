package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class CheckerReadsTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private static final CheckerReader.Run RUN =
      new CheckerReader.Run(
          "",
          "",
          Paths.get("/wt"),
          "proj@0123",
          "",
          new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
          RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
              .replies()
              .answer()
              .json(),
          "acceptance");

  private static final TaskState DEPLOYED =
      TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
          .alias("a1")
          .build()
          .withDeployment(
              new Deployment(
                  1,
                  List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                  1,
                  "digest"));

  @Test
  void acceptanceHoldsTheTurnWhileItReadsAndNobodyHoldsItOnceItAnswered(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer,
            event -> {});
    state.putTask("ABC-42", DEPLOYED);
    List<Turn.Holder> whileReading = new ArrayList<>();
    CheckerReader reader = mock(CheckerReader.class);
    when(reader.read(any()))
        .thenAnswer(call -> {
          TaskState task = state.task("ABC-42").orElseThrow();
          whileReading.add(
              Turns.next(
                  task.status(),
                  task.report(),
                  task.stopped(),
                  new Checkers(new TaskChart(Composition.builtIn())).of(task),
                  Arbiter.Profile.OFF));
          return new Answer<>(Optional.empty(), TokenUsage.NONE);
        });

    new CheckerReads(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            reader,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))),
            mock(CheckerFlows.class))
        .check("ABC-42", AcceptanceVerdict.ACCEPT, "digest", RUN);

    TaskState after = state.task("ABC-42").orElseThrow();
    assertThat(whileReading).containsExactly(new Turn.Holder.Role("acceptance"));
    assertThat(
            Turns.next(
                after.status(),
                after.report(),
                after.stopped(),
                new Checkers(new TaskChart(Composition.builtIn())).of(after),
                Arbiter.Profile.OFF))
        .isEqualTo(Turn.Holder.NOBODY);
  }

  @Test
  void flowsTheReadNamesRunAndTheirRowsJoinTheVerdict(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer,
            event -> {});
    state.putTask("ABC-42", DEPLOYED);
    AcceptanceVerdict read =
        new AcceptanceVerdict(
            "proj@0123",
            "",
            List.of(),
            "",
            "",
            new AcceptanceVerdict.Flows(
                List.of(new AcceptanceVerdict.Flows.Flow("AC-1", "")), "https://alpha.test"));
    AcceptanceVerdict withFlows =
        new AcceptanceVerdict(
            "proj@0123",
            "",
            List.of(new AcceptanceVerdict.Row("AC-1", AcceptanceVerdict.Status.NOT_MET, "failed")),
            "",
            "",
            AcceptanceVerdict.Flows.none());
    CheckerReader reader = mock(CheckerReader.class);
    when(reader.read(any())).thenReturn(new Answer<>(Optional.of(read), TokenUsage.NONE));
    CheckerFlows flows = mock(CheckerFlows.class);
    when(flows.run(read, Paths.get("/wt"), read.flows())).thenReturn(withFlows);

    Answer<AcceptanceVerdict> answer =
        new CheckerReads(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                reader,
                new TaskTurns(
                    state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))),
                flows)
            .check("ABC-42", AcceptanceVerdict.ACCEPT, "digest", RUN);

    assertThat(answer.facts()).contains(withFlows);
  }
}
