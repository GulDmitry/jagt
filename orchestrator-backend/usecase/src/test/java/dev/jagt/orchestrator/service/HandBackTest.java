package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.protocol.plan.PlanProbes;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.service.review.Rounds;
import dev.jagt.orchestrator.service.stop.BoardStop;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.Probe;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HandBackTest {

  @TempDir Path root;

  private final HandBack planner =
      new HandBack(mock(WorktreeChanges.class), mock(Rounds.class), mock(ConfigService.class));

  @Nested
  class WhereNoProjectIsConfigured {

    private HandBack handBack;

    @BeforeEach
    void readsAnEmptyProjectConfiguration() throws IOException {
      Path configFile =
          Files.writeString(
              root.resolve("jagt.yml"),
              """
              orchestrator:
                projects: {}
              """);
      OrchestratorPaths paths =
          new OrchestratorPaths(
              OrchestratorProperties.defaults()
                  .withRoot(root.toString())
                  .withConfigFile(configFile.toString()));
      ConfigService config =
          new ConfigService(
              new ConfigSource(paths, new FakeConfigInputs()),
              mock(AgentRuntime.class),
              new ActStops(new MasterBrake(paths), new BoardStop(paths, new FakeClock())),
              new Checkers(new TaskChart(Composition.builtIn())),
              new KeptNumbers(new ReachLedger()));
      handBack = new HandBack(mock(WorktreeChanges.class), mock(Rounds.class), config);
    }

    @Test
    void owesNoVerificationForProjectTheConfigurationNoLongerNames() {
      boolean owed =
          handBack.verificationOwed(
              TaskState.builder("gone", "/wt", TaskStatus.IN_PROGRESS).build());

      assertThat(owed).isFalse();
    }

    @Test
    void owesVerificationForTaskCarryingProbesWhereNoProjectCommandIsConfigured() {
      boolean owed =
          handBack.verificationOwed(
              TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                  .probes(List.of(Probe.specified(Probe.Kind.REF, List.of("main"), 0)))
                  .build());

      assertThat(owed).isTrue();
    }
  }

  @ParameterizedTest
  @CsvSource({"judge,true", "act,true", "off,false"})
  void readsWhetherTheMasterIsRunningFromTheConfiguredMode(String mode, boolean expected) {
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withArbiter(new ArbiterConfig(mode, null, null, null, null)));
    HandBack handBack = new HandBack(mock(WorktreeChanges.class), mock(Rounds.class), config);

    assertThat(handBack.arbiterReads()).isEqualTo(expected);
  }

  @Test
  void refusesPlanPutUpWithNoPlanFile() {
    PlanProbes.Read read =
        planner.planProbes(
            TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build());

    assertThat(read.probes()).isEmpty();
    assertThat(read.violations()).singleElement().asString().contains("## Acceptance criteria");
  }

  @Test
  void refusesPlanFileJagtCannotRead() throws IOException {
    Path plan = Files.writeString(root.resolve("plan.md"), "1. tag v1");
    assertThat(plan.toFile().setReadable(false)).isTrue();

    PlanProbes.Read read =
        planner.planProbes(
            TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build());

    assertThat(read.violations()).singleElement().asString().contains("jagt could not read it");
  }

  @Test
  void refusesPlanOfSeveralRepositoryTaskThatNamesNoRepositories() throws IOException {
    Files.writeString(root.resolve("plan.md"), "## Acceptance criteria\n- [here] it works\n");
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .repos(
                List.of(
                    new TaskRepo("api", root.toString(), null, null),
                    new TaskRepo("web", root.toString(), null, null)))
            .build();

    PlanProbes.Read read = planner.planProbes(task);

    assertThat(read.violations()).singleElement().asString().contains("## Repositories");
  }

  @Test
  void refusesProbeNamingRepositoryTheTaskLacks() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        """
        ## Acceptance criteria
        - [here] it works

        ```probes
        command repo=front npm test
        ```
        """);

    PlanProbes.Read read =
        planner.planProbes(
            TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build());

    assertThat(read.violations())
        .singleElement()
        .asString()
        .contains("command repo=front npm test")
        .contains("proj");
  }

  @Test
  void acceptsProbeNamingRepositoryOfTheTask() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        """
        ## Acceptance criteria
        - [here] it works

        ## Repositories
        - [changed] proj
        - [unaffected: no UI change] front

        ```probes
        command repo=front npm test
        ```
        """);
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .repos(
                List.of(
                    new TaskRepo("proj", root.toString(), null, null),
                    new TaskRepo("front", root.toString(), null, null)))
            .build();

    PlanProbes.Read read = planner.planProbes(task);

    assertThat(read.violations()).isEmpty();
  }

  @Test
  void acceptsProbeNamingChangedRepositoryOfTaskWithNoRepositoryYet() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        """
        ## Acceptance criteria
        - [here] it works

        ## Repositories
        - [changed] api

        ```probes
        command repo=api npm test
        ```
        """);
    TaskState task =
        TaskState.builder(List.of(TaskRepo.of(null, root.toString())), TaskStatus.PLAN_PENDING)
            .build();

    PlanProbes.Read read = planner.planProbes(task);

    assertThat(read.violations()).isEmpty();
  }

  @Test
  void refusesProbeNamingNoChangedRepositoryOfTaskWithNoRepositoryYet() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        """
        ## Acceptance criteria
        - [here] it works

        ## Repositories
        - [changed] api

        ```probes
        command repo=front npm test
        ```
        """);
    TaskState task =
        TaskState.builder(List.of(TaskRepo.of(null, root.toString())), TaskStatus.PLAN_PENDING)
            .build();

    PlanProbes.Read read = planner.planProbes(task);

    assertThat(read.violations())
        .singleElement()
        .asString()
        .contains("command repo=front npm test")
        .contains("api");
  }
}
