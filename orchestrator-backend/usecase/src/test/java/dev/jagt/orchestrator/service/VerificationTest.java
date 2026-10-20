package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VerificationTest {

    @Test
    void runsNothingForAProjectTheConfigurationNoLongerNames(@TempDir Path root) throws IOException {
        Path configFile = Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  projects: {}
                """);
        ConfigService config = new ConfigService(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withConfigFile(configFile.toString())), mock(AgentRuntime.class), mock(ActStops.class));
        Processes processes = mock(Processes.class);

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290))
                .failure(TaskState.builder("gone", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).isEmpty();
        verifyNoInteractions(processes);
    }

    @Test
    void namesTheProjectTheCommandAndTheExitOfAFailedRunWithTheTailOfItsErrors() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of(), List.of("./gradlew", "test"), null, null))));
        Processes processes = mock(Processes.class);
        String errors = IntStream.rangeClosed(1, 45).mapToObj(n -> "e" + n).collect(Collectors.joining("\n"));
        when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("./gradlew", "test"))))
                .thenReturn(new Processes.Result(1, "out", errors));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).hasValueSatisfying(text -> assertThat(text)
                .startsWith("[proj] `./gradlew test` exited 1\ne6\n").endsWith("\ne45").doesNotContain("e5\n"));
    }

    @Test
    void keepsAnErrorTailOfExactlyFortyLinesWhole() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of(), List.of("./gradlew", "test"), null, null))));
        Processes processes = mock(Processes.class);
        String errors = IntStream.rangeClosed(1, 40).mapToObj(n -> "e" + n).collect(Collectors.joining("\n"));
        when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("./gradlew", "test"))))
                .thenReturn(new Processes.Result(1, "", errors));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).hasValueSatisfying(text -> assertThat(text).contains("exited 1\ne1\ne2\n"));
    }

    @Test
    void relaysTheStandardOutputOfAFailedRunThatSaidNothingOnTheErrorStream() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of(), List.of("npm", "test"), null, null))));
        Processes processes = mock(Processes.class);
        when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("npm", "test"))))
                .thenReturn(new Processes.Result(2, "2 tests failed", ""));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).contains("[proj] `npm test` exited 2\n2 tests failed");
    }

    @Test
    void reportsNoFailureWhereTheCommandPassed() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of(), List.of("npm", "test"), null, null))));
        Processes processes = mock(Processes.class);
        when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("npm", "test"))))
                .thenReturn(new Processes.Result(0, "ok", ""));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).isEmpty();
    }

    @Test
    void reportsOnlyTheFirstOfTwoFailingRepositories() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "proj", new ProjectConfig("/repo", "origin/main", "dev", List.of(), List.of("npm", "test"), null, null),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of(), List.of("npm", "test"), null, null))));
        Processes processes = mock(Processes.class);
        when(processes.run(any(), any(), eq(List.of("npm", "test")))).thenReturn(new Processes.Result(1, "", "red"));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290))
                .failure(TaskState.builder(List.of(TaskRepo.of("proj", "/wt"), TaskRepo.of("web", "/wt-web")),
                        TaskStatus.VERIFYING).build());

        assertThat(failure).contains("[proj] `npm test` exited 1\nred");
    }

    @Test
    void runsNoProbeWhereThePlanDeclaredNone() {
        Processes processes = mock(Processes.class);

        List<Probe> probed = new Verification(mock(ConfigService.class), processes, new AcceptanceProbing(processes, 8290))
                .probed("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(probed).isEmpty();
        verifyNoInteractions(processes);
    }

    @Test
    void runsTheProbesThePlanDeclaredAndReturnsTheirResults() {
        Processes processes = mock(Processes.class);
        when(processes.run(any(), any(), anyMap(), eq(List.of("./scripts/check")))).thenReturn(new Processes.Result(0, "", ""));

        List<Probe> probed = new Verification(mock(ConfigService.class), processes, new AcceptanceProbing(processes, 8290))
                .probed("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING)
                        .probes(List.of(Probe.specified(Probe.Kind.COMMAND, List.of("./scripts/check"), 0))).build());

        assertThat(probed).singleElement().extracting(Probe::passed).isEqualTo(true);
    }
}
