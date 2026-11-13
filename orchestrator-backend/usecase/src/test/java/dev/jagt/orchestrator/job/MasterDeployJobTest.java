package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestMasterDeployJobBuilder.masterDeployJob;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class MasterDeployJobTest {

  private final StateService state = mock(StateService.class);
  private final ConfigService config = mock(ConfigService.class);
  private final MasterPress press = mock(MasterPress.class);

  static Stream<Arguments> pressCases() {
    return Stream.of(
        arguments(
            "reviewed task where deploy is not kept by the human",
            masterDeployJob().withMine(List.of("revert")),
            TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build(),
            List.of(TaskAction.DEPLOY)),
        arguments(
            "task approved on the host before the job looked at it",
            masterDeployJob(),
            TaskState.builder("demo", "/wt", TaskStatus.APPROVED).build(),
            List.of(TaskAction.DEPLOY)),
        arguments(
            "task where the master left an earlier step to the human",
            masterDeployJob(),
            TaskState.builder("demo", "/wt", TaskStatus.REVIEWED)
                .build()
                .withMaster(Master.defaults().withLeft("ship src/Login.java")),
            List.of(TaskAction.DEPLOY)),
        arguments(
            "deploy the human kept",
            masterDeployJob().withMine(List.of("deploy")),
            TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build(),
            List.of()),
        arguments(
            "request still open to review",
            masterDeployJob(),
            TaskState.builder("demo", "/wt", TaskStatus.CI_POLLING).build(),
            List.of()),
        arguments(
            "master that only judges",
            masterDeployJob().withMode("judge"),
            TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build(),
            List.of()),
        arguments(
            "revert the human kept on a deployed task",
            masterDeployJob().withMine(List.of("revert")),
            TaskState.builder("demo", "/wt", TaskStatus.DEPLOYED).build(),
            List.of()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("pressCases")
  void pressesExactlyTheActionsTheCaseAllows(
      String situation,
      TestMasterDeployJobBuilder builder,
      TaskState task,
      List<TaskAction> expected) {
    when(config.load()).thenReturn(builder.config());
    Map<String, TaskState> tasks = Map.of("ABC-1", task);
    when(state.tasks()).thenReturn(tasks);

    builder.build(config, press, state, Clock.systemUTC()).runOn(tasks);

    ArgumentCaptor<TaskAction> actions = ArgumentCaptor.captor();
    verify(press, times(expected.size())).press(anyString(), any(), actions.capture());
    assertThat(actions.getAllValues()).isEqualTo(expected);
  }

  @Test
  void pressesDeployOnceForAnArrivalThoughJagtRestarts(@TempDir Path root) {
    stateService()
        .withRoot(root)
        .build()
        .putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build());
    when(config.load()).thenReturn(masterDeployJob().config());
    StateService before = stateService().withRoot(root).build();
    masterDeployJob().build(config, press, before, Clock.systemUTC()).runOn(before.tasks());
    StateService restarted = stateService().withRoot(root).build();

    masterDeployJob().build(config, press, restarted, Clock.systemUTC()).runOn(restarted.tasks());

    verify(press, times(1)).press(eq("ABC-1"), any(), eq(TaskAction.DEPLOY));
  }
}
