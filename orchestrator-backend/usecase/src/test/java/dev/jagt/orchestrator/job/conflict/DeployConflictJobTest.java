package dev.jagt.orchestrator.job.conflict;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.deploy.ConflictEnding;
import dev.jagt.orchestrator.job.deploy.DeployPipelines;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeployConflictJobTest {

  @TempDir Path root;
  private final TaskState conflicted =
      TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
          .build()
          .withDeployment(
              new Deployment(1, List.of(Deployment.Target.of("proj", "dev")), 0, null)
                  .withTarget(
                      "proj",
                      target -> target.withConflict(Conflict.of(Deployment.Cause.MERGE, 1))));
  private final ConflictRead request = mock(ConflictRead.class);
  private final ConflictRead staged = mock(ConflictRead.class);
  private StateService state;

  @BeforeEach
  void stateAndTwoReadsEndingTheirOwnConflicts() {
    state = stateService().withRoot(root).build();
    when(request.ending()).thenReturn(ConflictEnding.REQUEST_CLOSED);
    when(staged.ending()).thenReturn(ConflictEnding.STAGED_FIX);
  }

  @Test
  void redPipelineConflictEndsAndTheTaskIsDeployedAgainWhenLaterPipelineOnTheDeployBranchIsGreen() {
    ConfigService config = mock(ConfigService.class);
    DeployPipelines pipelines = mock(DeployPipelines.class);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .build()
            .withDeployment(
                new Deployment(
                        1,
                        List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                        0,
                        null)
                    .withTarget(
                        "proj",
                        target ->
                            target.withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1)))));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of("proj", new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
    when(pipelines.redConflictDisproved(any())).thenReturn(true);

    new DeployConflictJob(
            List.of(
                new LaterGreenRead(
                    config,
                    pipelines,
                    new FlowReports(
                        state,
                        new FakeClock(),
                        new Checkers(new TaskChart(Composition.builtIn())),
                        new FlowRules(new TaskChart(Composition.builtIn()))))),
            state,
            new FakeClock())
        .runOn(state.tasks());

    TaskState read = state.task("ABC-42").orElseThrow();
    assertThat(read.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(Objects.requireNonNull(read.deployment()).conflictedTarget()).isEmpty();
  }

  @Test
  void readsTheHostOnceMinuteAndTheWorktreeEveryPass() {
    DeployConflictJob job =
        new DeployConflictJob(
            List.of(request, staged),
            state,
            Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

    job.runOn(Map.of("ABC-42", conflicted));
    job.runOn(Map.of("ABC-42", conflicted));

    verify(request, times(1)).read("ABC-42", conflicted);
    verify(staged, times(2)).read("ABC-42", conflicted);
  }

  @Test
  void taskConflictingAgainRightAfterItsConflictEndedReadsTheHostInTheFirstPass() {
    DeployConflictJob job =
        new DeployConflictJob(
            List.of(request, staged),
            state,
            Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

    job.runOn(Map.of("ABC-42", conflicted));
    job.runOn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()));
    job.runOn(Map.of("ABC-42", conflicted));

    verify(request, times(2)).read("ABC-42", conflicted);
  }

  @Test
  void readThatEndedTheConflictLeavesTheReadsAfterItUnread() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).build());
    when(request.read("ABC-42", conflicted)).thenReturn(true);

    new DeployConflictJob(List.of(request, staged), state, new FakeClock())
        .run("ABC-42", conflicted);

    verify(staged, never()).read(eq("ABC-42"), any());
  }

  @Test
  void readThatLeftTheConflictStandingHandsTheNextReadTheTaskAsItNowIs() {
    TaskState told = conflicted.withLastChangeWhy("its request was called gone");
    state.putTask("ABC-42", told);
    when(request.read("ABC-42", conflicted)).thenReturn(true);

    new DeployConflictJob(List.of(request, staged), state, new FakeClock())
        .run("ABC-42", conflicted);

    verify(staged).read("ABC-42", state.task("ABC-42").orElseThrow());
  }
}
