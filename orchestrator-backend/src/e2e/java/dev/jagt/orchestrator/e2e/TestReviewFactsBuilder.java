package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.task.review.ReviewFacts;
import java.util.List;
import lombok.With;

@With
public record TestReviewFactsBuilder(boolean approved, String pipeline, List<String> threads) {

  public static TestReviewFactsBuilder reviewFacts() {
    return new TestReviewFactsBuilder(false, "success", List.of());
  }

  public ReviewFacts build() {
    return new ReviewFacts(
        new ReviewFacts.Request(true, approved, false, false, 0),
        new ReviewFacts.Pipeline(pipeline, "", ""),
        new ReviewFacts.Discussion(threads, List.of(), List.of()));
  }
}
