package dev.jagt.orchestrator.job.checker;

import static dev.jagt.orchestrator.job.TestAcceptanceEnvironmentBuilder.acceptanceEnvironment;
import static dev.jagt.orchestrator.job.TestAcceptanceRoleBuilder.acceptanceRole;
import static dev.jagt.orchestrator.job.TestAcceptanceVerdictBuilder.acceptanceVerdict;
import static dev.jagt.orchestrator.job.TestAskedDeployBuilder.askedDeploy;
import static dev.jagt.orchestrator.job.checker.TestCheckerCheckJobBuilder.checkerCheckJob;
import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.AcceptanceEnding;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckerCheckJobTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final ConfigService config = mock(ConfigService.class);
  private final CheckerReads reader = mock(CheckerReads.class);
  private final CheckerOutcomes outcomes = mock(CheckerOutcomes.class);

  @TempDir Path root;

  private OrchestratorPaths paths;

  @BeforeEach
  void acceptanceOnAndTheReaderFindingEverythingMet() {
    paths = orchestratorPaths().withRoot(root).build();
    when(config.load()).thenReturn(checkerCheckJob().config());
    when(reader.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(acceptanceVerdict().build()), TokenUsage.NONE));
  }

  @Test
  void checksNothingWhereAcceptanceIsOff() {
    when(config.load()).thenReturn(ConfigFile.defaults());

    checkerCheckJob()
        .build(config, paths, reader, outcomes, Clock.systemUTC())
        .run("ABC-42", askedDeploy().build());

    verify(reader, never()).check(any(), any(), any(), any());
  }

  @Test
  void waitsForTheDeploysPipelineToBeGreenBeforeItChecks() {
    acceptanceEnvironment().build(root);

    checkerCheckJob()
        .build(config, paths, reader, outcomes, Clock.systemUTC())
        .run("ABC-42", askedDeploy().withAskedDigest(null).build());

    verify(reader, never()).check(any(), any(), any(), any());
  }

  @Test
  void checksDeployOnceThenLeavesItAloneAcrossRestart() {
    acceptanceEnvironment().build(root);
    when(outcomes.apply(any(), any(), any())).thenReturn(AcceptanceEnding.PASSED);
    checkerCheckJob()
        .build(config, paths, reader, outcomes, Clock.systemUTC())
        .run("ABC-42", askedDeploy().build());

    checkerCheckJob()
        .build(config, paths, reader, outcomes, Clock.systemUTC())
        .run("ABC-42", askedDeploy().build());

    verify(reader, times(1)).check(any(), any(), any(), any());
  }

  @Test
  void sendsRowNotMetBackToTheSession() {
    AcceptanceVerdict notMet =
        acceptanceVerdict()
            .withRows(
                List.of(
                    new AcceptanceVerdict.Row(
                        "list is sorted", AcceptanceVerdict.Status.NOT_MET, "GET /list unsorted")))
            .build();
    when(reader.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(notMet), TokenUsage.NONE));
    acceptanceEnvironment().build(root);
    TaskState task = askedDeploy().build();

    checkerCheckJob().build(config, paths, reader, outcomes, Clock.systemUTC()).run("ABC-42", task);

    verify(outcomes).apply(eq("ABC-42"), eq(task), eq(notMet));
  }

  @Test
  void handsMetVerdictToTheOutcomesToMarkPassed() {
    acceptanceEnvironment().build(root);

    checkerCheckJob()
        .build(config, paths, reader, outcomes, Clock.systemUTC())
        .run("ABC-42", askedDeploy().build());

    verify(outcomes).apply(eq("ABC-42"), any(), eq(acceptanceVerdict().build()));
  }

  @Test
  void runsCheckAgainOnTheSameShaWhereItsTurnWasLeftOpenUnanswered() throws IOException {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/brief.md.dist"),
        root.resolve("roles/acceptance/brief.md.dist"));
    acceptanceEnvironment().build(root);
    TaskState restarted = reducer.relayed(askedDeploy().build(), "accept", "digest", "", 1);

    checkerCheckJob()
        .build(config, paths, reader, outcomes, Clock.systemUTC())
        .run("ABC-42", restarted);

    verify(reader)
        .check(
            eq("ABC-42"),
            eq("again"),
            eq("digest"),
            argThat(run ->
                run.prompt().contains("The same check on the same sha")
                    && run.sha().equals("proj@0123456789abcdef")));
  }

  @Test
  void opensWithNoEnvironmentNamedWhereTheRolesTriggerOmitsEnvironmentNamed() {
    Path role = acceptanceRole().withTrigger("deployed, pipelines-green").build(root);

    checkerCheckJob()
        .withRoleFile(role)
        .build(config, paths, reader, outcomes, Clock.systemUTC())
        .run("ABC-42", askedDeploy().build());

    verify(reader).check(any(), any(), any(), any());
  }

  @Test
  void opensNothingWithNoEnvironmentNamedWhereTheRolesTriggerNamesIt() {
    Path role = acceptanceRole().build(root);

    checkerCheckJob()
        .withRoleFile(role)
        .build(config, paths, reader, outcomes, Clock.systemUTC())
        .run("ABC-42", askedDeploy().build());

    verify(reader, never()).check(any(), any(), any(), any());
  }

  @Test
  void opensOnTaskNoLongerOpenWhereTheRolesTriggerOmitsTrackerOpen() {
    Path role = acceptanceRole().build(root);
    acceptanceEnvironment().build(root);

    checkerCheckJob()
        .withRoleFile(role)
        .build(config, paths, reader, outcomes, Clock.systemUTC())
        .run("ABC-42", askedDeploy().withStatus(TaskStatus.DONE).build());

    verify(reader).check(any(), any(), any(), any());
  }
}
