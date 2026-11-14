package dev.jagt.orchestrator.job.deploy;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CodeHost;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.deploy.InfraRetries;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.deploy.FailedJob;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InfraRetryJobTest {

  private final InfraRetries retries = mock(InfraRetries.class);
  private final CodeHost pipelines = mock(CodeHost.class);
  private final TaskStops stops = mock(TaskStops.class);
  private final InfraRetryJob job = new InfraRetryJob(retries, pipelines, stops);
  private final InfraRetry armed =
      InfraRetry.armed(
          1_000, "shop", "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3);

  private final Answer<List<FailedJob>> diedOnInfrastructure =
      new Answer<>(
          Optional.of(List.of(new FailedJob("1", "unit-tests", "runner_system_failure", true))),
          TokenUsage.NONE);

  @BeforeEach
  void retryDueForTaskStillWaitingOnItsRedChecks() {
    when(retries.due()).thenReturn(Map.of("ABC-42", armed));
    when(retries.stillWaiting("ABC-42")).thenReturn(true);
    when(stops.moveDue("ABC-42")).thenReturn(true);
  }

  @Test
  void reRunsPipelineWhoseFailedJobsStillDiedOnInfrastructure() {
    when(pipelines.failedJobs("shop", "https://host.invalid/p/1")).thenReturn(diedOnInfrastructure);
    when(pipelines.retryFailed("shop", "https://host.invalid/p/1"))
        .thenReturn(new Answer<>(Optional.of("pending"), TokenUsage.NONE));

    job.run();

    verify(retries).sent("ABC-42", armed);
  }

  @Test
  void dropsTheRetryWhenHumanAlreadyMovedTheChecksOnFromRed() {
    when(retries.stillWaiting("ABC-42")).thenReturn(false);

    job.run();

    verify(retries).clear("ABC-42", false);
    verify(pipelines, never()).retryFailed("shop", "https://host.invalid/p/1");
  }

  @Test
  void dropsTheRetryOfTaskThatWasClosed() {
    when(retries.stillWaiting("ABC-42")).thenReturn(false);

    job.run();

    verify(retries).clear("ABC-42", false);
    verify(pipelines, never()).retryFailed("shop", "https://host.invalid/p/1");
  }

  @Test
  void dropsTheRetryOfStoppedTask() {
    when(stops.moveDue("ABC-42")).thenReturn(false);

    job.run();

    verify(retries).clear("ABC-42", false);
  }

  @Test
  void dropsTheRetryWhenTheHostNowSaysTheCodeFailed() {
    when(pipelines.failedJobs("shop", "https://host.invalid/p/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(List.of(new FailedJob("1", "unit-tests", "script_failure", false))),
                TokenUsage.NONE));

    job.run();

    verify(retries).clear("ABC-42", false);
    verify(pipelines, never()).retryFailed("shop", "https://host.invalid/p/1");
  }

  @Test
  void staysArmedAndStopsNoTaskWhileJagtCannotReachTheHost() {
    when(pipelines.failedJobs("shop", "https://host.invalid/p/1"))
        .thenReturn(
            Answer.unreached(
                new ReachLost(
                    "glab",
                    "git.example.invalid",
                    List.of("glab", "auth", "login"),
                    "glab not logged in")));

    job.run();

    verify(retries, never()).refused(anyString(), anyString());
    verify(retries, never()).clear(anyString(), anyBoolean());
  }

  @Test
  void stopsTheTaskWithTheHostsOwnWordsWhenItRefusesTheRetry() {
    when(pipelines.failedJobs("shop", "https://host.invalid/p/1")).thenReturn(diedOnInfrastructure);
    when(pipelines.retryFailed("shop", "https://host.invalid/p/1"))
        .thenReturn(Answer.failed(TokenUsage.NONE, "glab exited 1: 403 Forbidden"));

    job.run();

    verify(retries).refused("ABC-42", "glab exited 1: 403 Forbidden");
    verify(retries, never()).sent("ABC-42", armed);
  }
}
