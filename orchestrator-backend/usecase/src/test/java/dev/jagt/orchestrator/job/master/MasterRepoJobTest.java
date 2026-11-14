package dev.jagt.orchestrator.job.master;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterRepoJobTest {

  private final StateService state = mock(StateService.class);
  private final ConfigService config = mock(ConfigService.class);
  private final MasterPress press = mock(MasterPress.class);
  private final Checkers checkers = new Checkers(new TaskChart(Composition.builtIn()));
  private final MasterRepoJob job =
      new MasterRepoJob(state, config, press, new TaskTurns(state, Clock.systemUTC(), checkers));
  private final ConfigService.ConfigFile.MasterConfig acting =
      new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
  private final TaskState asking =
      TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
          .message("needs the registry")
          .report(AgentReport.QUESTION)
          .repoRequest("registry")
          .build();

  @Test
  void attachesTheRepositorySessionAskedForWhereTheMasterHoldsTheRight() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(acting));
    when(state.tasks()).thenReturn(Map.of("ABC-1", asking));

    job.run();

    verify(press).execute("ABC-1", TaskAction.ADD_REPO);
  }

  @Test
  void leavesTheRepositoryToTheHumanWhoKeptIt() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig(
                        "act", null, null, List.of("repo"), null)));
    when(state.tasks()).thenReturn(Map.of("ABC-1", asking));

    job.run();

    verify(press, never()).execute(anyString(), any());
  }

  @Test
  void attachesNothingWhereTheMasterOnlyJudges() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
    when(state.tasks()).thenReturn(Map.of("ABC-1", asking));

    job.run();

    verify(press, never()).execute(anyString(), any());
  }

  @Test
  void attachesNothingForTaskThatAskedForNoRepository() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(acting));
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                    .message("which rule?")
                    .report(AgentReport.QUESTION)
                    .build()));

    job.run();

    verify(press, never()).execute(anyString(), any());
  }

  @Nested
  class WhereTheStateLivesOnDisk {

    @TempDir Path root;

    @BeforeEach
    void theRequestIsOnTheDiskAndTheMasterActs() {
      stateService().withRoot(root).build().putTask("ABC-1", asking);
      when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(acting));
    }

    @Test
    void pressesOnceWhileTheSameRequestStandsThoughJagtRestarts() {
      StateService before = stateService().withRoot(root).build();
      new MasterRepoJob(before, config, press, new TaskTurns(before, Clock.systemUTC(), checkers))
          .run();
      StateService restarted = stateService().withRoot(root).build();

      new MasterRepoJob(
              restarted, config, press, new TaskTurns(restarted, Clock.systemUTC(), checkers))
          .run();

      verify(press, times(1)).execute("ABC-1", TaskAction.ADD_REPO);
    }

    @Test
    void pressesAgainWhenTheSameProjectIsAskedForAfterTheFirstRequestEnded() {
      StateService tasks = stateService().withRoot(root).build();
      MasterRepoJob onDisk =
          new MasterRepoJob(
              tasks, config, press, new TaskTurns(tasks, Clock.systemUTC(), checkers));

      onDisk.run();
      tasks.updateTask("ABC-1", task -> task.withRepoRequest(null));
      onDisk.run();
      tasks.updateTask("ABC-1", task -> task.withRepoRequest("registry"));
      onDisk.run();

      verify(press, times(2)).execute("ABC-1", TaskAction.ADD_REPO);
    }
  }
}
