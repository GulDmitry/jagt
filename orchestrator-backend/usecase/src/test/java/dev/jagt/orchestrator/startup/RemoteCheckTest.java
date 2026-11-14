package dev.jagt.orchestrator.startup;

import static dev.jagt.orchestrator.startup.TestRemoteProjectBuilder.remoteProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RemoteCheckTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final Processes processes = mock(Processes.class);
  private final RemoteCheck check = new RemoteCheck(configService, processes);

  @Test
  void acceptsRemoteTheRepositoryLists() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "origin\nupstream\n", ""));
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("shop", remoteProject().withRemote("upstream").build())));

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesRemoteTheRepositoryDoesNotListNamingTheKey() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("shop", remoteProject().withRemote("upstream").build())));
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "origin\n", ""));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.remote");
  }

  @Test
  void refusesTheDefaultOriginOfRepositoryWithNoRemote() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("shop", remoteProject().build())));
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.remote");
  }

  @Test
  void refusesNoRemoteBesideCodeHostNamingBothKeys() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        remoteProject().withCodeHost("gitlab").withRemote("none").build())));

    assertThat(check.problems())
        .singleElement()
        .satisfies(problem -> {
          assertThat(problem.key()).isEqualTo("orchestrator.projects.shop.remote");
          assertThat(problem.text())
              .contains("orchestrator.projects.shop.remote", "orchestrator.projects.shop.codeHost");
        });
  }

  @Test
  void refusesNoRemoteBesideTheOrchestratorsCodeHostNamingThatKey() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withCodeHost("github")
                .withProjects(Map.of("shop", remoteProject().withRemote("none").build())));

    assertThat(check.problems())
        .singleElement()
        .satisfies(problem -> assertThat(problem.text()).contains("orchestrator.codeHost"));
  }

  @Test
  void acceptsNoRemoteWithNoCodeHostOrNone() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        remoteProject().withCodeHost("none").withRemote("none").build(),
                        "web",
                        remoteProject().withRemote("none").build())));

    assertThat(check.problems()).isEmpty();
  }
}
