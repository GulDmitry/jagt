package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteCheckTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final Processes processes = mock(Processes.class);
    private final RemoteCheck check = new RemoteCheck(configService, processes);

    private static ProjectConfig project(String codeHost, String remote) {
        return new ProjectConfig("/repo/shop", "main", "dev", List.of(), List.of(), null, null, codeHost, null, null,
                null, null, remote, null, null);
    }

    @Test
    void acceptsARemoteTheRepositoryLists() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("shop", project(null, "upstream"))));
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(0, "origin\nupstream\n", ""));

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void refusesARemoteTheRepositoryDoesNotListNamingTheKey() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("shop", project(null, "upstream"))));
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(0, "origin\n", ""));

        assertThat(check.problems()).extracting(Problem::key).containsExactly("orchestrator.projects.shop.remote");
    }

    @Test
    void refusesTheDefaultOriginOfARepositoryWithNoRemote() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("shop", project(null, null))));
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", ""));

        assertThat(check.problems()).extracting(Problem::key).containsExactly("orchestrator.projects.shop.remote");
    }

    @Test
    void refusesNoRemoteBesideACodeHostNamingBothKeys() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("shop", project("gitlab", "none"))));

        assertThat(check.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("orchestrator.projects.shop.remote");
            assertThat(problem.text()).contains("orchestrator.projects.shop.remote", "orchestrator.projects.shop.codeHost");
        });
    }

    @Test
    void refusesNoRemoteBesideTheOrchestratorsCodeHostNamingThatKey() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withCodeHost("github")
                .withProjects(Map.of("shop", project(null, "none"))));

        assertThat(check.problems()).singleElement()
                .satisfies(problem -> assertThat(problem.text()).contains("orchestrator.codeHost"));
    }

    @Test
    void acceptsNoRemoteWithNoCodeHostOrNone() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "shop", project("none", "none"), "web", project(null, "none"))));

        assertThat(check.problems()).isEmpty();
    }
}
