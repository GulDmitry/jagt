package dev.jagt.orchestrator.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class RoundReadingHostMergedTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final ReviewRound reviewRound = mock(ReviewRound.class);
  private final MergedRequests mergedRequests = mock(MergedRequests.class);

  @Test
  void squashMergedRequestIsSeenMergedByTheHostsWord(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer,
            event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .alias("a1")
            .mrUrl("http://mr/1")
            .build());
    RoundReading reading = new RoundReading(state, reviewRound, mergedRequests);
    when(reviewRound.read("ABC-1", "proj", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, true, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    reading.read("ABC-1");

    verify(mergedRequests).hostSaidMerged("ABC-1", "proj");
  }
}
