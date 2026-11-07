package dev.jagt.orchestrator.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.DeployStep;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.MergedRequests;
import dev.jagt.orchestrator.service.ReviewReader;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConflictRequestReadJobTest {

  private final ReviewReader reader = mock(ReviewReader.class);
  private final MergedRequests merged = mock(MergedRequests.class);

  @Test
  void takesTheConflictedRepositoryOutOfTheTaskWhereTheHostClosedItsRequest() {
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

    new ConflictRequestReadJob(reader, merged).run("ABC-42", red);

    verify(merged).hostSaidClosed("ABC-42", beta);
  }

  @Test
  void takesTheRepositoryTheMergeConflictedOnOutOfTheTaskWhereTheHostClosedItsRequest() {
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

    new ConflictRequestReadJob(reader, merged).run("ABC-42", conflicted);

    verify(merged).hostSaidClosed("ABC-42", beta);
  }

  @Test
  void readsNoRequestWhereTheDeployWaitsOnARevertThatConflicted() {
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

    new ConflictRequestReadJob(reader, merged).run("ABC-42", conflicted);

    verifyNoInteractions(reader, merged);
  }
}
