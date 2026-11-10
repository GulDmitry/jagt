package dev.jagt.orchestrator.service.master;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

public final class RoundQuotesFixture {

  public final RoundFacts facts;
  public final StateService state;
  public final MasterReview reviews;
  public final RoundQuotes quotes;
  private final Path worktree;

  public RoundQuotesFixture(Path worktree, RoundFacts facts) {
    this.worktree = worktree;
    this.facts = facts;
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(worktree.toString())
                    .withStateFile(worktree.resolve("state.json").toString())),
            new TaskReducer(new TaskChart(Composition.builtIn())),
            event -> {});
    reviews = new MasterReview(state);
    quotes = new RoundQuotes(facts, mock(MasterDecisions.class), reviews);
  }

  public TaskState pending() {
    return TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
        .alias("a1")
        .lastActiveTimestamp(5_000)
        .build();
  }

  public static MasterReview.Review ready(String diff, @Nullable String kind, String inputs) {
    return new MasterReview.Review(
        1_000,
        diff,
        kind,
        inputs,
        List.of(
            new MasterReview.Read(
                "acceptance", new Judgement("", "ready", List.of(), "", List.of(), ""))));
  }

  public TaskState store(TaskState task, MasterReview.Review review) {
    state.putTask("a1", task);
    reviews.write("a1", task, review, "");
    return state.task("a1").orElseThrow();
  }

  public TaskState storeQuietRead(TaskState task) {
    when(facts.request(any())).thenReturn("Summary");
    when(facts.notes(any())).thenReturn("");
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));
    when(facts.humanSaid(any())).thenReturn(Optional.of(List.of()));
    return store(task, ready("abc123", "PLAIN", quotes.inputs("ABC-1", task).orElseThrow()));
  }
}
