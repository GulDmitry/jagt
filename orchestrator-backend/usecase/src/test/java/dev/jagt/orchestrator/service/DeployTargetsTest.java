package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeployTargetsTest {

    private final ConfigService config = mock(ConfigService.class);
    private final DeployTargets targets = new DeployTargets(config, mock(GitDeploy.class));

    @Test
    void namesTheDeployBranchAProjectIsConfiguredWith() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/srv/api", "origin/main", "dev", List.of()))));

        assertThat(targets.deployBranch("api")).contains("dev");
    }

    @Test
    void namesNoDeployBranchForAProjectConfiguredWithABlankOne() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/srv/api", "origin/main", " ", List.of()))));

        assertThat(targets.deployBranch("api")).isEmpty();
    }

    @Test
    void namesNoDeployBranchForAProjectThatIsNoLongerConfigured() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        assertThat(targets.deployBranch("gone")).isEmpty();
    }
}
