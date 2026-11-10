package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.List;
import java.util.Optional;

public final class ReviewReadFixtures {

  private ReviewReadFixtures() {}

  public static TaskRepo api() {
    return new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api");
  }

  public static TaskRepo web() {
    return new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");
  }

  public static TaskState polling(TaskRepo... repos) {
    return TaskState.builder(List.of(repos), TaskStatus.CI_POLLING).alias("a1").build();
  }

  public static TaskState pollingOneRepository() {
    return TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
        .alias("a1")
        .mrUrl("http://mr/1")
        .build();
  }

  public static ReviewFacts.Request request(boolean approved) {
    return new ReviewFacts.Request(true, approved, false, false, 0);
  }

  public static ReviewFacts.Discussion quiet() {
    return new ReviewFacts.Discussion(List.of(), List.of(), List.of());
  }

  public static Answer<ReviewFacts> answer(
      ReviewFacts.Request request, ReviewFacts.Pipeline pipeline, ReviewFacts.Discussion talk) {
    return new Answer<>(Optional.of(new ReviewFacts(request, pipeline, talk)), TokenUsage.NONE);
  }

  public static Answer<ReviewFacts> answer(boolean approved, String pipeline) {
    return answer(request(approved), new ReviewFacts.Pipeline(pipeline, "", ""), quiet());
  }

  public static Answer<ReviewFacts> answer(
      boolean approved, String pipeline, String failure, String pipelineUrl) {
    return answer(
        request(approved), new ReviewFacts.Pipeline(pipeline, failure, pipelineUrl), quiet());
  }

  public static Answer<ReviewFacts> unreadable() {
    return new Answer<>(Optional.empty(), TokenUsage.NONE);
  }
}
