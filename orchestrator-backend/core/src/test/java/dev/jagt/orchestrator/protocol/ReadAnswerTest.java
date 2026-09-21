package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadAnswerTest {

    @Test
    void refusesARequestThatExistsWithoutTheBranchThatWouldBecomeTheTask() {
        var facts = new MergeRequestFacts(true, "", "main", "Widget layout");

        assertThat(MergeRequestRead.violations(facts)).extracting(Violation::field)
                .containsExactly("sourceBranch");
    }

    @Test
    void acceptsAHostSayingThereIsNoSuchRequest() {
        assertThat(MergeRequestRead.violations(new MergeRequestFacts(false, "", "", ""))).isEmpty();
    }

    @Test
    void refusesAFailingJobsLinesQuotedUnderAVerdictThatIsNotRed() {
        var facts = new ReviewFacts(true, false, "success", "step 3 exited 1", List.of(), 0);

        assertThat(ReviewRead.violations(facts)).extracting(Violation::field)
                .containsExactly("pipelineFailure");
    }

    @Test
    void acceptsTheFailingJobsLinesWhereTheRunActuallyFailed() {
        var facts = new ReviewFacts(true, false, "failed", "step 3 exited 1", List.of(), 0);

        assertThat(ReviewRead.violations(facts)).isEmpty();
    }
}
