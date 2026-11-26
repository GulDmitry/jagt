package dev.jagt.orchestrator.job.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.testing.FakeHost;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class DeployOnApprovalJobTest {

  private final ConfigService config = mock(ConfigService.class);
  private final CommandService commands = mock(CommandService.class);
  @TempDir Path root;
  private StateService state;
  private DeployOnApprovalJob job;
  private final TaskState approved = TaskState.builder("demo", "/wt", TaskStatus.APPROVED).build();

  @BeforeEach
  void setUp() {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(properties, FakeHost.machine()),
            new TaskReducer(new TaskChart(Composition.builtIn())),
            event -> {});
    job = new DeployOnApprovalJob(config, commands, state);
  }

  @Test
  void deploysApprovedTaskNamingTheCodeHostAsWhoActed() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withReviewing(
                    new ConfigFile.Reviewing(
                        null, null, null, new ConfigFile.DeployConfig(true, null))));
    state.putTask("ABC-1", approved);
    ActionOrigin[] actedAs = new ActionOrigin[1];
    when(commands.execute("ABC-1", TaskAction.DEPLOY))
        .thenAnswer(call -> {
          actedAs[0] = OriginContext.current();
          return "deployed";
        });

    job.runOn(Map.of("ABC-1", approved));

    assertThat(actedAs[0]).isEqualTo(ActionOrigin.CODE_HOST);
  }

  @Test
  void leavesApprovedTaskToTheHumanWhenOnApprovalIsOff() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withReviewing(
                    new ConfigFile.Reviewing(
                        null, null, null, new ConfigFile.DeployConfig(false, null))));
    state.putTask("ABC-1", approved);

    job.runOn(Map.of("ABC-1", approved));

    verify(commands, never()).execute(anyString(), any());
  }

  @Test
  void leavesApprovedTaskToTheHumanWhenOnApprovalIsUnset() {
    when(config.load()).thenReturn(ConfigFile.defaults());
    state.putTask("ABC-1", approved);

    job.runOn(Map.of("ABC-1", approved));

    verify(commands, never()).execute(anyString(), any());
  }

  @Test
  void doesNothingWhenTheMasterOrTheHumanDeployedFirst() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withReviewing(
                    new ConfigFile.Reviewing(
                        null, null, null, new ConfigFile.DeployConfig(true, null))));
    state.putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.DEPLOYED).build());

    job.runOn(Map.of("ABC-1", approved));

    verify(commands, never()).execute(anyString(), any());
  }

  @Test
  void leavesTaskNotYetApprovedAlone() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withReviewing(
                    new ConfigFile.Reviewing(
                        null, null, null, new ConfigFile.DeployConfig(true, null))));
    TaskState reviewed = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();

    job.runOn(Map.of("ABC-1", reviewed));

    verify(commands, never()).execute(anyString(), any());
  }

  @Test
  void deploysOnceForOneArrivalAtApproved() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withReviewing(
                    new ConfigFile.Reviewing(
                        null, null, null, new ConfigFile.DeployConfig(true, null))));
    state.putTask("ABC-1", approved);

    job.runOn(Map.of("ABC-1", approved));
    job.runOn(Map.of("ABC-1", approved));

    verify(commands, times(1)).execute("ABC-1", TaskAction.DEPLOY);
  }

  @Test
  void warnsOnceWhenTheDeployIsRefused() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withReviewing(
                    new ConfigFile.Reviewing(
                        null, null, null, new ConfigFile.DeployConfig(true, null))));
    state.putTask("ABC-1", approved);
    when(commands.execute("ABC-1", TaskAction.DEPLOY))
        .thenThrow(new IllegalStateException("checks are not green"));

    try (FakeLogCapture log = new FakeLogCapture(DeployOnApprovalJob.class)) {
      job.runOn(Map.of("ABC-1", approved));
      job.runOn(Map.of("ABC-1", approved));

      assertThat(log.events())
          .singleElement()
          .satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getMessage()).isEqualTo("deploy on approval refused");
          });
    }
  }

  @Test
  void doesNotPressAgainForTheSameArrivalWhenJagtRestarts() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withReviewing(
                    new ConfigFile.Reviewing(
                        null, null, null, new ConfigFile.DeployConfig(true, null))));
    state.putTask("ABC-1", approved);
    job.runOn(Map.of("ABC-1", approved));

    new DeployOnApprovalJob(config, commands, state).runOn(Map.of("ABC-1", approved));

    verify(commands, times(1)).execute("ABC-1", TaskAction.DEPLOY);
  }
}
