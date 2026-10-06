package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MasterReviewJobTest {

    @Test
    void leavesARoundAloneWhoseQuestionTheMasterAlreadyLeftToTheHuman() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .message("outcome=question — override the gate?").build();

        boolean reviewable = MasterReviewJob.reviewable(asking, false, task -> true);

        assertThat(reviewable).isFalse();
    }
}
