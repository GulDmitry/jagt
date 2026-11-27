package dev.jagt.orchestrator.job.conflict;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.assistant.ReviewReader;
import dev.jagt.orchestrator.service.repo.MergedRequests;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeJobFaults;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RequestClosedReadTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;
  private final ReviewReader reader = mock(ReviewReader.class);
  private final MergedRequests merged = mock(MergedRequests.class);
  private final TaskRepo alpha =
      new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha");
  private final TaskRepo beta =
      new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta");
  private final TaskState opened =
      reducer.deployStepped(
          TaskState.builder(List.of(alpha, beta), TaskStatus.DEPLOY_CONFLICT).build(),
          new DeployStep.Opened(
              List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
          1_000);
  private final TaskState betaMerge =
      reducer.deployStepped(
          opened, new DeployStep.Conflicted("beta", Deployment.Cause.MERGE), 1_100);
  private final Answer<ReviewFacts> requestClosed =
      new Answer<>(
          Optional.of(
              new ReviewFacts(
                  new ReviewFacts.Request(true, false, false, true, 0),
                  new ReviewFacts.Pipeline("none", "", ""),
                  new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
          TokenUsage.NONE);
  private StateService state;
  private final FakeJobFaults faults = new FakeJobFaults();
  private RequestClosedRead read;

  @BeforeEach
  void readOverStateOnDisk() {
    state = stateService().withRoot(root).build();
    read = new RequestClosedRead(reader, merged, state, faults);
  }

  @Test
  void takesTheConflictedRepositoryOutOfTheTaskWhereTheHostClosedItsRequest() {
    TaskState red =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_100);
    when(reader.read("ABC-42", "http://mr/beta")).thenReturn(requestClosed);
    when(merged.hostSaidClosed("ABC-42", beta)).thenReturn(true);

    read.read("ABC-42", red);

    verify(merged).hostSaidClosed("ABC-42", beta);
  }

  @Test
  void takesTheRepositoryTheMergeConflictedOnOutOfTheTaskWhereTheHostClosedItsRequest() {
    when(reader.read("ABC-42", "http://mr/beta")).thenReturn(requestClosed);

    read.read("ABC-42", betaMerge);

    verify(merged).hostSaidClosed("ABC-42", beta);
  }

  @Test
  void callsTheRequestOfTheConflictedPrimaryRepositoryGoneOnceAndReadsItNoMore() {
    TaskState red =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("alpha", Deployment.Cause.RED_PIPELINE), 1_100);
    state.putTask("ABC-42", red);
    when(reader.read("ABC-42", "http://mr/alpha")).thenReturn(requestClosed);

    read.read("ABC-42", state.task("ABC-42").orElseThrow());
    read.read("ABC-42", state.task("ABC-42").orElseThrow());

    verify(reader, times(1)).read("ABC-42", "http://mr/alpha");
    assertThat(
            state
                .task("ABC-42")
                .orElseThrow()
                .round()
                .toldOrNone()
                .said(ToldOnce.Kind.CONFLICT_REQUEST_GONE, "http://mr/alpha"))
        .isTrue();
  }

  @ParameterizedTest
  @CsvSource({"true,true", "false,false"})
  void saysTheTaskChangedOnlyWhereTheHostClosedTheRequest(boolean closed, boolean changed) {
    when(reader.read("ABC-42", "http://mr/beta"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, closed, 0),
                        new ReviewFacts.Pipeline("none", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(merged.hostSaidClosed("ABC-42", beta)).thenReturn(true);

    boolean said = read.read("ABC-42", betaMerge);

    assertThat(said).isEqualTo(changed);
  }

  @Test
  void saysNothingChangedWhereTheRequestCouldNotBeRead() {
    when(reader.read("ABC-42", "http://mr/beta")).thenThrow(new IllegalStateException("down"));

    boolean said = read.read("ABC-42", betaMerge);

    assertThat(said).isFalse();
    assertThat(faults.causes()).containsExactly("down");
  }
}
