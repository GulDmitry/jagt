package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.Remote;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigRemotesTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final ConfigRemotes remotes = new ConfigRemotes(configService);

    @Test
    void namesTheRemoteOfTheConfiguredProjectAtThatRepository() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("shop",
                new ProjectConfig(new ProjectConfig.Repository("/repo/shop", List.of(), null, null), new ProjectConfig.Branches("main", "dev", "none"), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of(), null)))));

        assertThat(remotes.of(Path.of("/repo/shop"))).contains(Remote.LOCAL);
    }

    @Test
    void namesNoRemoteForARepositoryNoProjectPointsAt() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

        assertThat(remotes.of(Path.of("/repo/other"))).isEmpty();
    }
}
