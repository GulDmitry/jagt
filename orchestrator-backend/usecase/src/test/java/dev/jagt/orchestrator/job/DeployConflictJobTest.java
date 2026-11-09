package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.ConflictEnding;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class DeployConflictJobTest {

  @Test
  void aRedPipelineConflictEndsAndTheTaskIsDeployedAgainWhenALaterPipelineOnTheDeployBranchIsGreen(
      @TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
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
                new LaterGreenRead(config, pipelines, new FlowReports(state, Clock.systemUTC()))),
            state,
            Clock.systemUTC())
        .runOn(state.tasks());

    TaskState read = state.task("ABC-42").orElseThrow();
    assertThat(read.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(read.deployment().conflictedTarget()).isEmpty();
  }

  @Test
  void readsTheHostOnceAMinuteAndTheWorktreeEveryPass(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState conflicted =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .build()
            .withDeployment(
                new Deployment(
                        1,
                        List.of(Deployment.Target.of("proj", "dev")),
                        0,
                        null)
                    .withTarget(
                        "proj",
                        target -> target.withConflict(Conflict.of(Deployment.Cause.MERGE, 1))));
    ConflictRead request = mock(ConflictRead.class);
    ConflictRead staged = mock(ConflictRead.class);
    when(request.ending()).thenReturn(ConflictEnding.REQUEST_CLOSED);
    when(staged.ending()).thenReturn(ConflictEnding.STAGED_FIX);
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
  void aTaskConflictingAgainRightAfterItsConflictEndedReadsTheHostInTheFirstPass(
      @TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState conflicted =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .build()
            .withDeployment(
                new Deployment(1, List.of(Deployment.Target.of("proj", "dev")), 0, null)
                    .withTarget(
                        "proj",
                        target -> target.withConflict(Conflict.of(Deployment.Cause.MERGE, 1))));
    ConflictRead request = mock(ConflictRead.class);
    ConflictRead staged = mock(ConflictRead.class);
    when(request.ending()).thenReturn(ConflictEnding.REQUEST_CLOSED);
    when(staged.ending()).thenReturn(ConflictEnding.STAGED_FIX);
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
  void aReadThatEndedTheConflictLeavesTheReadsAfterItUnread(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState conflicted =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .build()
            .withDeployment(
                new Deployment(
                        1,
                        List.of(Deployment.Target.of("proj", "dev")),
                        0,
                        null)
                    .withTarget(
                        "proj",
                        target -> target.withConflict(Conflict.of(Deployment.Cause.MERGE, 1))));
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).build());
    ConflictRead request = mock(ConflictRead.class);
    ConflictRead staged = mock(ConflictRead.class);
    when(request.ending()).thenReturn(ConflictEnding.REQUEST_CLOSED);
    when(staged.ending()).thenReturn(ConflictEnding.STAGED_FIX);
    when(request.read("ABC-42", conflicted)).thenReturn(true);

    new DeployConflictJob(List.of(request, staged), state, Clock.systemUTC())
        .run("ABC-42", conflicted);

    verify(staged, never()).read(eq("ABC-42"), any());
  }

  @Test
  void aReadThatLeftTheConflictStandingHandsTheNextReadTheTaskAsItNowIs(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskState conflicted =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .build()
            .withDeployment(
                new Deployment(
                        1,
                        List.of(Deployment.Target.of("proj", "dev")),
                        0,
                        null)
                    .withTarget(
                        "proj",
                        target -> target.withConflict(Conflict.of(Deployment.Cause.MERGE, 1))));
    TaskState told = conflicted.withLastChangeWhy("its request was called gone");
    state.putTask("ABC-42", told);
    ConflictRead request = mock(ConflictRead.class);
    ConflictRead staged = mock(ConflictRead.class);
    when(request.ending()).thenReturn(ConflictEnding.REQUEST_CLOSED);
    when(staged.ending()).thenReturn(ConflictEnding.STAGED_FIX);
    when(request.read("ABC-42", conflicted)).thenReturn(true);

    new DeployConflictJob(List.of(request, staged), state, Clock.systemUTC())
        .run("ABC-42", conflicted);

    verify(staged).read("ABC-42", state.task("ABC-42").orElseThrow());
  }
}
