package dev.jagt.orchestrator.service.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeployTargetsTest {

  private final ConfigService config = mock(ConfigService.class);
  private final DeployTargets targets = new DeployTargets(config);

  @Test
  void namesTheDeployBranchProjectIsConfiguredWith() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of("api", new ProjectConfig("/srv/api", "origin/main", "dev", List.of()))));

    assertThat(targets.deployBranch("api")).contains("dev");
  }

  @Test
  void namesNoDeployBranchForProjectConfiguredWithBlankOne() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of("api", new ProjectConfig("/srv/api", "origin/main", " ", List.of()))));

    assertThat(targets.deployBranch("api")).isEmpty();
  }

  @Test
  void namesNoDeployBranchForProjectThatIsNoLongerConfigured() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThat(targets.deployBranch("gone")).isEmpty();
  }
}
