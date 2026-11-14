package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.review.MergeRequestFacts;
import org.junit.jupiter.api.Test;

class MergeRequestReadTest {

  @Test
  void refusesRequestThatExistsWithoutTheBranchThatWouldBecomeTheTask() {
    var facts = new MergeRequestFacts(true, "", "main", "Widget layout");

    assertThat(MergeRequestRead.violations(facts))
        .extracting(Violation::field)
        .containsExactly("sourceBranch");
  }

  @Test
  void acceptsHostSayingThereIsNoSuchRequest() {
    assertThat(MergeRequestRead.violations(new MergeRequestFacts(false, "", "", ""))).isEmpty();
  }
}
