package dev.jagt.orchestrator.job.checker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.job.checker.workspace.CheckerWorkspace;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class CheckerReadsTest {

  private final FakeHost host = FakeHost.machine();

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
                    .withStateFile(root.resolve("state.json").toString()),
                host),
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
                  task.standing().status(),
                  task.standing().report(),
                  task.work().stopped() ? StopHold.HUMAN : StopHold.NONE,
                  new Checkers(new TaskChart(Composition.builtIn())).of(task),
                  Arbiter.Profile.OFF,
                  Facts.defaults()));
          return new Answer<>(Optional.empty(), TokenUsage.NONE);
        });

    new CheckerReads(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            reader,
            new TaskTurns(
                state, new FakeClock(), new Checkers(new TaskChart(Composition.builtIn()))),
            mock(CheckerFlows.class),
            mock(CheckerWorkspace.class))
        .check("ABC-42", AcceptanceVerdict.ACCEPT, "digest", RUN);

    TaskState after = state.task("ABC-42").orElseThrow();
    assertThat(whileReading).containsExactly(new Turn.Holder.Role("acceptance"));
    assertThat(
            Turns.next(
                after.standing().status(),
                after.standing().report(),
                after.work().stopped() ? StopHold.HUMAN : StopHold.NONE,
                new Checkers(new TaskChart(Composition.builtIn())).of(after),
                Arbiter.Profile.OFF,
                Facts.defaults()))
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
                    .withStateFile(root.resolve("state.json").toString()),
                host),
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
                    state, new FakeClock(), new Checkers(new TaskChart(Composition.builtIn()))),
                flows,
                mock(CheckerWorkspace.class))
            .check("ABC-42", AcceptanceVerdict.ACCEPT, "digest", RUN);

    assertThat(answer.facts()).contains(withFlows);
  }

  @Test
  void theWorkspaceIsOfferedOnceTheReadAnswered(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                host),
            reducer,
            event -> {});
    state.putTask("ABC-42", DEPLOYED);
    AcceptanceVerdict read = AcceptanceVerdict.failed("proj@0123", "");
    CheckerReader reader = mock(CheckerReader.class);
    when(reader.read(any())).thenReturn(new Answer<>(Optional.of(read), TokenUsage.NONE));
    CheckerFlows flows = mock(CheckerFlows.class);
    when(flows.run(any(), any(), any())).thenReturn(read);
    CheckerWorkspace workspace = mock(CheckerWorkspace.class);

    new CheckerReads(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            reader,
            new TaskTurns(
                state, new FakeClock(), new Checkers(new TaskChart(Composition.builtIn()))),
            flows,
            workspace)
        .check("ABC-42", AcceptanceVerdict.ACCEPT, "digest", RUN);

    verify(workspace).offer("ABC-42");
  }

  @Test
  void theWorkspaceIsNotOfferedWhenTheReadAnsweredNothing(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                host),
            reducer,
            event -> {});
    state.putTask("ABC-42", DEPLOYED);
    CheckerReader reader = mock(CheckerReader.class);
    when(reader.read(any())).thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));
    CheckerWorkspace workspace = mock(CheckerWorkspace.class);

    new CheckerReads(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            reader,
            new TaskTurns(
                state, new FakeClock(), new Checkers(new TaskChart(Composition.builtIn()))),
            mock(CheckerFlows.class),
            workspace)
        .check("ABC-42", AcceptanceVerdict.ACCEPT, "digest", RUN);

    verifyNoInteractions(workspace);
  }
}
