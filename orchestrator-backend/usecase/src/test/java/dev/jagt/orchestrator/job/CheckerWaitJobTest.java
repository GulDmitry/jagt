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
import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckerWaitJobTest {

  private static final ConfigFile ON =
      ConfigFile.defaults()
          .withRoles(Map.of("acceptance", new ConfigFile.RoleConfig(true, null)));

  @TempDir Path root;
  private final ConfigService config = mock(ConfigService.class);
  private final EnvironmentProbe probe = mock(EnvironmentProbe.class);
  private CheckerRecords records;
  private CheckerWaitJob job;

  @BeforeEach
  void anAcceptanceRoleWatchingOneEnvironment() throws IOException {
    when(config.load()).thenReturn(ON);
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        "environments: [{url: \"https://stage.example.com\","
            + " landed: \"https://stage.example.com/health\"}]\n");
    records = new CheckerRecords(paths, Clock.systemUTC());
    job =
        new CheckerWaitJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                config,
                paths),
            records,
            probe);
  }

  @Test
  void holdsTheCheckWhileTheEnvironmentDoesNotAnswerItsProbeHoweverLongItWaits() {
    when(probe.answers("https://stage.example.com/health")).thenReturn(false);
    records.put("ABC-42", "digest", "proj@0123456789abcdef", AcceptanceEnding.WAITING);

    job
        .run("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    assertThat(records.settled("ABC-42", "digest")).isTrue();
  }

  @Test
  void runsTheCheckAgainOnceTheEnvironmentAnswersItsLandedProbe() {
    when(probe.answers("https://stage.example.com/health")).thenReturn(true);
    records.put("ABC-42", "digest", "proj@0123456789abcdef", AcceptanceEnding.WAITING);

    job
        .run("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    assertThat(records.settled("ABC-42", "digest")).isFalse();
  }

  @Test
  void keepsFinishedChecksRecordWhateverTheEnvironmentAnswers() {
    when(probe.answers("https://stage.example.com/health")).thenReturn(true);
    records.put("ABC-42", "digest", "proj@0123456789abcdef", AcceptanceEnding.PASSED);

    job
        .run("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    assertThat(records.settled("ABC-42", "digest")).isTrue();
  }
}
