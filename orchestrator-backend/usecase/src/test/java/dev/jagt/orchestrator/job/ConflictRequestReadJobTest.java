package dev.jagt.orchestrator.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.DeployStep;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.MergedRequests;
import dev.jagt.orchestrator.service.ReviewReader;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ConflictRequestReadJobTest {

  private final ReviewReader reader = mock(ReviewReader.class);
  private final MergedRequests merged = mock(MergedRequests.class);
  private final Notifications notifications = mock(Notifications.class);

  private StateService stateOf(Path root) {
    return new StateService(
        new JsonMapper(),
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString())));
  }

  @Test
  void takesTheConflictedRepositoryOutOfTheTaskWhereTheHostClosedItsRequest(@TempDir Path root) {
    TaskRepo beta = new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta");
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    beta),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        TaskReducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
            1_000);
    TaskState red =
        TaskReducer.deployStepped(
            opened, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_100);
    when(reader.read("ABC-42", "http://mr/beta"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, true, 0),
                        new ReviewFacts.Pipeline("none", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(merged.hostSaidClosed("ABC-42", beta)).thenReturn(true);

    new ConflictRequestReadJob(reader, merged, stateOf(root), notifications).run("ABC-42", red);

    verify(merged).hostSaidClosed("ABC-42", beta);
    verifyNoInteractions(notifications);
  }

  @Test
  void takesTheRepositoryTheMergeConflictedOnOutOfTheTaskWhereTheHostClosedItsRequest(
      @TempDir Path root) {
    TaskRepo beta = new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta");
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    beta),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        TaskReducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
            1_000);
    TaskState conflicted =
        TaskReducer.deployStepped(
            opened, new DeployStep.Conflicted("beta", Deployment.Cause.MERGE), 1_100);
    when(reader.read("ABC-42", "http://mr/beta"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, true, 0),
                        new ReviewFacts.Pipeline("none", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    new ConflictRequestReadJob(reader, merged, stateOf(root), notifications).run("ABC-42", conflicted);

    verify(merged).hostSaidClosed("ABC-42", beta);
  }

  @Test
  void callsTheRequestOfTheConflictedPrimaryRepositoryGoneOnceAndReadsItNoMore(
      @TempDir Path root) {
    StateService state = stateOf(root);
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        TaskReducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
            1_000);
    TaskState red =
        TaskReducer.deployStepped(
            opened, new DeployStep.Conflicted("alpha", Deployment.Cause.RED_PIPELINE), 1_100);
    state.putTask("ABC-42", red);
    when(reader.read("ABC-42", "http://mr/alpha"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, true, 0),
                        new ReviewFacts.Pipeline("none", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    ConflictRequestReadJob job =
        new ConflictRequestReadJob(reader, merged, state, notifications);

    job.run("ABC-42", state.task("ABC-42").orElseThrow());
    job.run("ABC-42", state.task("ABC-42").orElseThrow());

    verify(reader, times(1)).read("ABC-42", "http://mr/alpha");
    verify(notifications, times(1)).send(any());
  }

  @Test
  void readsNoRequestWhereTheDeployWaitsOnARevertThatConflicted(@TempDir Path root) {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        TaskReducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
            1_000);
    TaskState conflicted =
        TaskReducer.deployStepped(
            opened, new DeployStep.Conflicted("beta", Deployment.Cause.REVERT), 1_100);

    new ConflictRequestReadJob(reader, merged, stateOf(root), notifications).run("ABC-42", conflicted);

    verifyNoInteractions(reader, merged);
  }
}
