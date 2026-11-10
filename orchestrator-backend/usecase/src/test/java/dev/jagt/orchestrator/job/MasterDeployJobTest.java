package dev.jagt.orchestrator.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class MasterDeployJobTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final StateService state = mock(StateService.class);
  private final ConfigService config = mock(ConfigService.class);
  private final MasterPress press = mock(MasterPress.class);

  @Test
  void deploysReviewedTaskWhereDeployIsNotKeptByTheHuman() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig(
                        "act", null, null, List.of("revert"), null)));
    Map<String, TaskState> tasks =
        Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build());
    when(state.tasks()).thenReturn(tasks);

    new MasterDeployJob(
            config,
            press,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))))
        .runOn(tasks);

    verify(press).press(eq("ABC-1"), any(), eq(TaskAction.DEPLOY));
  }

  @Test
  void deploysTaskApprovedOnTheHostBeforeTheJobLookedAtIt() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
    Map<String, TaskState> tasks =
        Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.APPROVED).build());
    when(state.tasks()).thenReturn(tasks);

    new MasterDeployJob(
            config,
            press,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))))
        .runOn(tasks);

    verify(press).press(eq("ABC-1"), any(), eq(TaskAction.DEPLOY));
  }

  @Test
  void leavesTheDeployToTheHumanWhoKeptIt() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig(
                        "act", null, null, List.of("deploy"), null)));
    Map<String, TaskState> tasks =
        Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build());
    when(state.tasks()).thenReturn(tasks);

    new MasterDeployJob(
            config,
            press,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))))
        .runOn(tasks);

    verify(press, never()).press(anyString(), any(), any());
  }

  @Test
  void deploysNothingWhoseRequestIsStillOpenToReview() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
    Map<String, TaskState> tasks =
        Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.CI_POLLING).build());
    when(state.tasks()).thenReturn(tasks);

    new MasterDeployJob(
            config,
            press,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))))
        .runOn(tasks);

    verify(press, never()).press(anyString(), any(), any());
  }

  @Test
  void pressesDeployOnceForAnArrivalThoughJagtRestarts(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    new StateService(new JsonMapper(), paths, reducer)
        .putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build());
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
    StateService before = new StateService(new JsonMapper(), paths, reducer);
    new MasterDeployJob(
            config,
            press,
            new TaskTurns(
                before, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))))
        .runOn(before.tasks());
    StateService restarted = new StateService(new JsonMapper(), paths, reducer);

    new MasterDeployJob(
            config,
            press,
            new TaskTurns(
                restarted, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))))
        .runOn(restarted.tasks());

    verify(press, times(1)).press(eq("ABC-1"), any(), eq(TaskAction.DEPLOY));
  }

  @Test
  void deploysNothingWhereTheMasterOnlyJudges() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
    Map<String, TaskState> tasks =
        Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build());
    when(state.tasks()).thenReturn(tasks);

    new MasterDeployJob(
            config,
            press,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))))
        .runOn(tasks);

    verify(press, never()).press(anyString(), any(), any());
  }

  @Test
  void revertsNothingOnItsOwnOfDeployedTaskWhereTheHumanKeptTheRevert() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig(
                        "act", null, null, List.of("revert"), null)));
    Map<String, TaskState> tasks =
        Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.DEPLOYED).build());
    when(state.tasks()).thenReturn(tasks);

    new MasterDeployJob(
            config,
            press,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))))
        .runOn(tasks);

    verify(press, never()).press(anyString(), any(), any());
  }

  @Test
  void deploysTaskWhereTheMasterLeftAnEarlierStepToTheHuman() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1",
            TaskState.builder("demo", "/wt", TaskStatus.REVIEWED)
                .build()
                .withMaster(Master.defaults().withLeft("ship src/Login.java")));
    when(state.tasks()).thenReturn(tasks);

    new MasterDeployJob(
            config,
            press,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))))
        .runOn(tasks);

    verify(press).press(eq("ABC-1"), any(), eq(TaskAction.DEPLOY));
  }
}
