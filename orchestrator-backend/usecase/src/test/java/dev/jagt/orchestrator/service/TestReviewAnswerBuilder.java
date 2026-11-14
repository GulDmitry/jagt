package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import lombok.With;

@With
public record TestReviewAnswerBuilder(
    boolean exists,
    boolean approved,
    boolean closed,
    long openedAt,
    String status,
    String failure,
    String url,
    ReviewFacts.Discussion discussion) {

  public static TestReviewAnswerBuilder reviewAnswer() {
    return new TestReviewAnswerBuilder(
        true,
        false,
        false,
        0,
        "success",
        "",
        "",
        new ReviewFacts.Discussion(List.of(), List.of(), List.of()));
  }

  public Answer<ReviewFacts> build() {
    return new Answer<>(
        Optional.of(
            new ReviewFacts(
                new ReviewFacts.Request(exists, approved, false, closed, openedAt),
                new ReviewFacts.Pipeline(status, failure, url),
                discussion)),
        TokenUsage.NONE);
  }
}
