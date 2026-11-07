package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadAnswerTest {

  @Test
  void refusesARequestThatExistsWithoutTheBranchThatWouldBecomeTheTask() {
    var facts = new MergeRequestFacts(true, "", "main", "Widget layout");

    assertThat(MergeRequestRead.violations(facts))
        .extracting(Violation::field)
        .containsExactly("sourceBranch");
  }

  @Test
  void acceptsAHostSayingThereIsNoSuchRequest() {
    assertThat(MergeRequestRead.violations(new MergeRequestFacts(false, "", "", ""))).isEmpty();
  }

  @Test
  void refusesAFailingJobsLinesQuotedUnderAVerdictThatIsNotRed() {
    var facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "step 3 exited 1", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));

    assertThat(ReviewRead.violations(facts))
        .extracting(Violation::field)
        .containsExactly("pipelineFailure");
  }

  @Test
  void acceptsWhyTheListingCouldNotBeGotWhereTheChecksAreUnknown() {
    var facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("unknown", "list_pipelines: 403 Forbidden", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));

    assertThat(ReviewRead.violations(facts)).isEmpty();
  }

  @Test
  void acceptsTheFailingJobsLinesWhereTheRunActuallyFailed() {
    var facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "step 3 exited 1", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));

    assertThat(ReviewRead.violations(facts)).isEmpty();
  }
}
