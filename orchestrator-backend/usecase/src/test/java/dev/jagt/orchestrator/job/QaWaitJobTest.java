package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.port.EnvironmentProbe;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QaWaitJobTest {

  private static final ConfigFile ON =
      ConfigFile.defaults()
          .withQa(
              new ConfigFile.QaConfig(
                  true,
                  null,
                  List.of(
                      new ConfigFile.QaEnvironment(
                          "https://stage.example.com", "https://stage.example.com/health"))));

  @Test
  void holdsTheCheckWhileTheEnvironmentDoesNotAnswerItsProbeHoweverLongItWaits(@TempDir Path root) {
    ConfigService config = mock(ConfigService.class);
    EnvironmentProbe probe = mock(EnvironmentProbe.class);
    when(config.load()).thenReturn(ON);
    when(probe.answers("https://stage.example.com/health")).thenReturn(false);
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    CheckerRecords records = new CheckerRecords(paths, Clock.systemUTC());
    records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.WAITING);

    new CheckerWaitJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")), config),
            records,
            probe)
        .run("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    assertThat(records.settled("ABC-42", "digest")).isTrue();
  }

  @Test
  void runsTheCheckAgainOnceTheEnvironmentAnswersItsLandedProbe(@TempDir Path root) {
    ConfigService config = mock(ConfigService.class);
    EnvironmentProbe probe = mock(EnvironmentProbe.class);
    when(config.load()).thenReturn(ON);
    when(probe.answers("https://stage.example.com/health")).thenReturn(true);
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    CheckerRecords records = new CheckerRecords(paths, Clock.systemUTC());
    records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.WAITING);

    new CheckerWaitJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")), config),
            records,
            probe)
        .run("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    assertThat(records.settled("ABC-42", "digest")).isFalse();
  }

  @Test
  void keepsAFinishedChecksRecordWhateverTheEnvironmentAnswers(@TempDir Path root) {
    ConfigService config = mock(ConfigService.class);
    EnvironmentProbe probe = mock(EnvironmentProbe.class);
    when(config.load()).thenReturn(ON);
    when(probe.answers("https://stage.example.com/health")).thenReturn(true);
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    CheckerRecords records = new CheckerRecords(paths, Clock.systemUTC());
    records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.PASSED);

    new CheckerWaitJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")), config),
            records,
            probe)
        .run("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    assertThat(records.settled("ABC-42", "digest")).isTrue();
  }
}
