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
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeployOnApprovalJobTest {

  private final ConfigService config = mock(ConfigService.class);
  private final CommandService commands = mock(CommandService.class);
  private final StateService state = mock(StateService.class);
  private final DeployOnApprovalJob job = new DeployOnApprovalJob(config, commands, state);
  private final TaskState approved = TaskState.builder("demo", "/wt", TaskStatus.APPROVED).build();

  @Test
  void deploysApprovedTaskNamingTheCodeHostAsWhoActed() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withReviewing(
                    new ConfigFile.Reviewing(
                        null, null, null, new ConfigFile.DeployConfig(true, null))));
    when(state.task("ABC-1")).thenReturn(Optional.of(approved));
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
    when(state.task("ABC-1")).thenReturn(Optional.of(approved));

    job.runOn(Map.of("ABC-1", approved));

    verify(commands, never()).execute(anyString(), any());
  }

  @Test
  void leavesApprovedTaskToTheHumanWhenOnApprovalIsUnset() {
    when(config.load()).thenReturn(ConfigFile.defaults());
    when(state.task("ABC-1")).thenReturn(Optional.of(approved));

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
    when(state.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("demo", "/wt", TaskStatus.DEPLOYED).build()));

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
    when(state.task("ABC-1")).thenReturn(Optional.of(approved));

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
    when(state.task("ABC-1")).thenReturn(Optional.of(approved));
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
}
