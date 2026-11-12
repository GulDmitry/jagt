package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.task.Remote;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigRemotesTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final ConfigRemotes remotes = new ConfigRemotes(configService);

  @Test
  void namesTheRemoteOfTheConfiguredProjectAtThatRepository() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        TestProjectConfigBuilder.projectConfig()
                            .withPath("/repo/shop")
                            .withBaseBranch("main")
                            .withRemote("none")
                            .build())));

    assertThat(remotes.of(Path.of("/repo/shop"))).contains(Remote.LOCAL);
  }

  @Test
  void namesNoRemoteForRepositoryNoProjectPointsAt() {
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThat(remotes.of(Path.of("/repo/other"))).isEmpty();
  }
}
