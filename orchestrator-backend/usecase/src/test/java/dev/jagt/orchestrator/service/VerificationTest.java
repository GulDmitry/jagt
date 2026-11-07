package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
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
    void runsNothingForAProjectTheConfigurationNoLongerNames() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of()));
        Processes processes = mock(Processes.class);

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .failure(TaskState.builder("gone", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).isEmpty();
        verifyNoInteractions(processes);
    }

    @Test
    void stopsTheVerifyCommandAfterTheMinutesTheProjectConfigures() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(new ProjectConfig.Repository("/repo", List.of(), null, null), new ProjectConfig.Branches("origin/main", "dev", null), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of("./gradlew", "test"), 5)))));
        Processes processes = mock(Processes.class);
        when(processes.run(Path.of("/wt"), Duration.ofMinutes(5), List.of("./gradlew", "test")))
                .thenReturn(new Processes.Result(1, "", "red"));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).isPresent();
    }

    @Test
    void stopsTheVerifyCommandAfterTwentyMinutesWhereTheProjectConfiguresNone() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(new ProjectConfig.Repository("/repo", List.of(), null, null), new ProjectConfig.Branches("origin/main", "dev", null), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of("./gradlew", "test"), null)))));
        Processes processes = mock(Processes.class);
        when(processes.run(Path.of("/wt"), Duration.ofMinutes(20), List.of("./gradlew", "test")))
                .thenReturn(new Processes.Result(1, "", "red"));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).isPresent();
    }

    @Test
    void namesTheProjectTheCommandAndTheExitOfAFailedRunWithTheTailOfItsErrors() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(new ProjectConfig.Repository("/repo", List.of(), null, null), new ProjectConfig.Branches("origin/main", "dev", null), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of("./gradlew", "test"), null)))));
        Processes processes = mock(Processes.class);
        String errors = IntStream.rangeClosed(1, 45).mapToObj(n -> "e" + n).collect(Collectors.joining("\n"));
        when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("./gradlew", "test"))))
                .thenReturn(new Processes.Result(1, "out", errors));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).hasValueSatisfying(text -> assertThat(text)
                .startsWith("[proj] `./gradlew test` exited 1\ne6\n").endsWith("\ne45").doesNotContain("e5\n"));
    }

    @Test
    void keepsAnErrorTailOfExactlyFortyLinesWhole() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(new ProjectConfig.Repository("/repo", List.of(), null, null), new ProjectConfig.Branches("origin/main", "dev", null), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of("./gradlew", "test"), null)))));
        Processes processes = mock(Processes.class);
        String errors = IntStream.rangeClosed(1, 40).mapToObj(n -> "e" + n).collect(Collectors.joining("\n"));
        when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("./gradlew", "test"))))
                .thenReturn(new Processes.Result(1, "", errors));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).hasValueSatisfying(text -> assertThat(text).contains("exited 1\ne1\ne2\n"));
    }

    @Test
    void relaysTheStandardOutputOfAFailedRunThatSaidNothingOnTheErrorStream() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(new ProjectConfig.Repository("/repo", List.of(), null, null), new ProjectConfig.Branches("origin/main", "dev", null), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of("npm", "test"), null)))));
        Processes processes = mock(Processes.class);
        when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("npm", "test"))))
                .thenReturn(new Processes.Result(2, "2 tests failed", ""));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).contains("[proj] `npm test` exited 2\n2 tests failed");
    }

    @Test
    void reportsNoFailureWhereTheCommandPassed() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(new ProjectConfig.Repository("/repo", List.of(), null, null), new ProjectConfig.Branches("origin/main", "dev", null), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of("npm", "test"), null)))));
        Processes processes = mock(Processes.class);
        when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("npm", "test"))))
                .thenReturn(new Processes.Result(0, "ok", ""));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).isEmpty();
    }

    @Test
    void reportsOnlyTheFirstOfTwoFailingRepositories() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "proj", new ProjectConfig(new ProjectConfig.Repository("/repo", List.of(), null, null), new ProjectConfig.Branches("origin/main", "dev", null), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of("npm", "test"), null)),
                "web", new ProjectConfig(new ProjectConfig.Repository("/web", List.of(), null, null), new ProjectConfig.Branches("origin/main", "dev", null), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of("npm", "test"), null)))));
        Processes processes = mock(Processes.class);
        when(processes.run(any(), any(), eq(List.of("npm", "test")))).thenReturn(new Processes.Result(1, "", "red"));

        Optional<String> failure = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .failure(TaskState.builder(List.of(TaskRepo.of("proj", "/wt"), TaskRepo.of("web", "/wt-web")),
                        TaskStatus.VERIFYING).build());

        assertThat(failure).contains("[proj] `npm test` exited 1\nred");
    }

    @Test
    void runsNoProbeWhereThePlanDeclaredNone() {
        Processes processes = mock(Processes.class);

        List<Probe> probed = new Verification(mock(ConfigService.class), processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .probed("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

        assertThat(probed).isEmpty();
        verifyNoInteractions(processes);
    }

    @Test
    void runsTheProbesThePlanDeclaredAndReturnsTheirResults() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        Processes processes = mock(Processes.class);
        when(processes.run(any(), any(), anyMap(), eq(List.of("./scripts/check")))).thenReturn(new Processes.Result(0, "", ""));

        List<Probe> probed = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .probed("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING)
                        .probes(List.of(Probe.specified(Probe.Kind.COMMAND, List.of("./scripts/check"), 0))).build());

        assertThat(probed).singleElement().extracting(Probe::passed).isEqualTo(true);
    }

    @Test
    void namesTheProjectsOwnRemoteWhereAProbeIsRefusedForAPushThatCouldSkipItsCheck() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig(new ProjectConfig.Repository("/repo", List.of(), null, null), new ProjectConfig.Branches("upstream/main", "dev", "upstream"), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of(), null)))));
        Processes processes = mock(Processes.class);

        List<Probe> probed = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .probed("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING)
                        .probes(List.of(Probe.specified(Probe.Kind.COMMAND,
                                List.of("git", "push", "--no-verify", "upstream", "ABC-42"), 0))).build());

        assertThat(probed).singleElement().extracting(Probe::excerpt).asString().contains("`git push upstream ABC-42`");
    }

    @Test
    void runsAProbeNamingTheSecondRepositoryInThatWorktreeAndOthersInThePrimaryOne() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        Processes processes = mock(Processes.class);
        when(processes.run(eq(Path.of("/wt-front")), any(), anyMap(), eq(List.of("npm", "test"))))
                .thenReturn(new Processes.Result(0, "", ""));
        when(processes.run(eq(Path.of("/wt")), any(), anyMap(), eq(List.of("./scripts/check"))))
                .thenReturn(new Processes.Result(1, "", ""));

        List<Probe> probed = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .probed("ABC-42", TaskState.builder(List.of(TaskRepo.of("proj", "/wt"), TaskRepo.of("front", "/wt-front")),
                                TaskStatus.VERIFYING)
                        .probes(List.of(Probe.specified(Probe.Kind.COMMAND, "front", List.of("npm", "test"), 0),
                                Probe.specified(Probe.Kind.COMMAND, List.of("./scripts/check"), 0))).build());

        assertThat(probed).extracting(Probe::passed).containsExactly(true, false);
    }

    @Test
    void namesTheNamedRepositorysOwnRemoteWhereItsProbeIsRefused() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("front",
                new ProjectConfig(new ProjectConfig.Repository("/front", List.of(), null, null), new ProjectConfig.Branches("upstream/main", "dev", "upstream"), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of(), null)))));
        Processes processes = mock(Processes.class);

        List<Probe> probed = new Verification(config, processes, new AcceptanceProbing(processes, 8290), mock(WorktreeChanges.class))
                .probed("ABC-42", TaskState.builder(List.of(TaskRepo.of("proj", "/wt"), TaskRepo.of("front", "/wt-front")),
                                TaskStatus.VERIFYING)
                        .probes(List.of(Probe.specified(Probe.Kind.COMMAND, "front",
                                List.of("git", "push", "--no-verify", "upstream", "ABC-42"), 0))).build());

        assertThat(probed).singleElement().extracting(Probe::excerpt).asString().contains("`git push upstream ABC-42`");
    }
}
