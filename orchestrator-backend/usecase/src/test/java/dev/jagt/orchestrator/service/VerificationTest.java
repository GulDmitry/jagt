package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.Probe;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class VerificationTest {

  private final ConfigService config = mock(ConfigService.class);
  private final Processes processes = mock(Processes.class);
  private final Verification verification =
      new Verification(
          config,
          processes,
          new AcceptanceProbing(processes, new ToolGate(List.of()), 8290),
          mock(WorktreeChanges.class));
  private final ConfigService.ConfigFile gradleProject =
      ConfigService.ConfigFile.defaults()
          .withProjects(
              Map.of(
                  "proj",
                  TestProjectConfigBuilder.projectConfig()
                      .withVerifyCommand(List.of("./gradlew", "test"))
                      .build()));
  private final ConfigService.ConfigFile npmProject =
      ConfigService.ConfigFile.defaults()
          .withProjects(
              Map.of(
                  "proj",
                  TestProjectConfigBuilder.projectConfig()
                      .withVerifyCommand(List.of("npm", "test"))
                      .build()));

  @Test
  void runsNothingForProjectTheConfigurationNoLongerNames() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of()));

    Optional<String> failure =
        verification.failure(TaskState.builder("gone", "/wt", TaskStatus.VERIFYING).build());

    assertThat(failure).isEmpty();
    verifyNoInteractions(processes);
  }

  @Test
  void stopsTheVerifyCommandAfterTheMinutesTheProjectConfigures() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "proj",
                        TestProjectConfigBuilder.projectConfig()
                            .withVerifyCommand(List.of("./gradlew", "test"))
                            .withVerifyMinutes(5)
                            .build())));
    when(processes.run(Path.of("/wt"), Duration.ofMinutes(5), List.of("./gradlew", "test")))
        .thenReturn(new Processes.Result(1, "", "red"));

    Optional<String> failure =
        verification.failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

    assertThat(failure).isPresent();
  }

  @Test
  void stopsTheVerifyCommandAfterTwentyMinutesWhereTheProjectConfiguresNone() {
    when(config.load()).thenReturn(gradleProject);
    when(processes.run(Path.of("/wt"), Duration.ofMinutes(20), List.of("./gradlew", "test")))
        .thenReturn(new Processes.Result(1, "", "red"));

    Optional<String> failure =
        verification.failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

    assertThat(failure).isPresent();
  }

  @Test
  void namesTheProjectTheCommandAndTheExitOfFailedRunWithTheTailOfItsErrors() {
    when(config.load()).thenReturn(gradleProject);
    String errors =
        IntStream.rangeClosed(1, 45).mapToObj(n -> "e" + n).collect(Collectors.joining("\n"));
    when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("./gradlew", "test"))))
        .thenReturn(new Processes.Result(1, "out", errors));

    Optional<String> failure =
        verification.failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

    assertThat(failure)
        .hasValueSatisfying(text ->
            assertThat(text)
                .startsWith("[proj] `./gradlew test` exited 1\ne6\n")
                .endsWith("\ne45")
                .doesNotContain("e5\n"));
  }

  @Test
  void keepsAnErrorTailOfExactlyFortyLinesWhole() {
    when(config.load()).thenReturn(gradleProject);
    String errors =
        IntStream.rangeClosed(1, 40).mapToObj(n -> "e" + n).collect(Collectors.joining("\n"));
    when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("./gradlew", "test"))))
        .thenReturn(new Processes.Result(1, "", errors));

    Optional<String> failure =
        verification.failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

    assertThat(failure).hasValueSatisfying(text -> assertThat(text).contains("exited 1\ne1\ne2\n"));
  }

  @Test
  void relaysTheStandardOutputOfFailedRunThatSaidNothingOnTheErrorStream() {
    when(config.load()).thenReturn(npmProject);
    when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("npm", "test"))))
        .thenReturn(new Processes.Result(2, "2 tests failed", ""));

    Optional<String> failure =
        verification.failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

    assertThat(failure).contains("[proj] `npm test` exited 2\n2 tests failed");
  }

  @Test
  void reportsNoFailureWhereTheCommandPassed() {
    when(config.load()).thenReturn(npmProject);
    when(processes.run(eq(Path.of("/wt")), any(), eq(List.of("npm", "test"))))
        .thenReturn(new Processes.Result(0, "ok", ""));

    Optional<String> failure =
        verification.failure(TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

    assertThat(failure).isEmpty();
  }

  @Test
  void reportsOnlyTheFirstOfTwoFailingRepositories() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "proj",
                            TestProjectConfigBuilder.projectConfig()
                                .withVerifyCommand(List.of("npm", "test"))
                                .build(),
                        "web",
                            TestProjectConfigBuilder.projectConfig()
                                .withPath("/web")
                                .withVerifyCommand(List.of("npm", "test"))
                                .build())));
    when(processes.run(any(), any(), eq(List.of("npm", "test"))))
        .thenReturn(new Processes.Result(1, "", "red"));

    Optional<String> failure =
        verification.failure(
            TaskState.builder(
                    List.of(TaskRepo.of("proj", "/wt"), TaskRepo.of("web", "/wt-web")),
                    TaskStatus.VERIFYING)
                .build());

    assertThat(failure).contains("[proj] `npm test` exited 1\nred");
  }

  @Test
  void runsNoProbeWhereThePlanDeclaredNone() {

    List<Probe> probed =
        verification.probed(
            "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());

    assertThat(probed).isEmpty();
    verifyNoInteractions(processes);
  }

  @Test
  void runsTheProbesThePlanDeclaredAndReturnsTheirResults() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(processes.run(any(), any(), anyMap(), eq(List.of("./scripts/check"))))
        .thenReturn(new Processes.Result(0, "", ""));

    List<Probe> probed =
        verification.probed(
            "ABC-42",
            TaskState.builder("proj", "/wt", TaskStatus.VERIFYING)
                .probes(List.of(Probe.specified(Probe.Kind.COMMAND, List.of("./scripts/check"), 0)))
                .build());

    assertThat(probed).singleElement().extracting(Probe::passed).isEqualTo(true);
  }

  @Test
  void namesTheProjectsOwnRemoteWhereProbeIsRefusedForPushThatCouldSkipItsCheck() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "proj",
                        TestProjectConfigBuilder.projectConfig()
                            .withBaseBranch("upstream/main")
                            .withRemote("upstream")
                            .build())));

    List<Probe> probed =
        verification.probed(
            "ABC-42",
            TaskState.builder("proj", "/wt", TaskStatus.VERIFYING)
                .probes(
                    List.of(
                        Probe.specified(
                            Probe.Kind.COMMAND,
                            List.of("git", "push", "--no-verify", "upstream", "ABC-42"),
                            0)))
                .build());

    assertThat(probed)
        .singleElement()
        .extracting(Probe::excerpt)
        .asString()
        .contains("`git push upstream ABC-42`");
  }

  @Test
  void runsProbeNamingTheSecondRepositoryInThatWorktreeAndOthersInThePrimaryOne() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(processes.run(eq(Path.of("/wt-front")), any(), anyMap(), eq(List.of("npm", "test"))))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(eq(Path.of("/wt")), any(), anyMap(), eq(List.of("./scripts/check"))))
        .thenReturn(new Processes.Result(1, "", ""));

    List<Probe> probed =
        verification.probed(
            "ABC-42",
            TaskState.builder(
                    List.of(TaskRepo.of("proj", "/wt"), TaskRepo.of("front", "/wt-front")),
                    TaskStatus.VERIFYING)
                .probes(
                    List.of(
                        Probe.specified(Probe.Kind.COMMAND, "front", List.of("npm", "test"), 0),
                        Probe.specified(Probe.Kind.COMMAND, List.of("./scripts/check"), 0)))
                .build());

    assertThat(probed).extracting(Probe::passed).containsExactly(true, false);
  }

  @Test
  void namesTheNamedRepositorysOwnRemoteWhereItsProbeIsRefused() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "front",
                        TestProjectConfigBuilder.projectConfig()
                            .withPath("/front")
                            .withBaseBranch("upstream/main")
                            .withRemote("upstream")
                            .build())));

    List<Probe> probed =
        verification.probed(
            "ABC-42",
            TaskState.builder(
                    List.of(TaskRepo.of("proj", "/wt"), TaskRepo.of("front", "/wt-front")),
                    TaskStatus.VERIFYING)
                .probes(
                    List.of(
                        Probe.specified(
                            Probe.Kind.COMMAND,
                            "front",
                            List.of("git", "push", "--no-verify", "upstream", "ABC-42"),
                            0)))
                .build());

    assertThat(probed)
        .singleElement()
        .extracting(Probe::excerpt)
        .asString()
        .contains("`git push upstream ABC-42`");
  }
}
