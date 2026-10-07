package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MasterReviewJobTest {

    @Test
    void leavesARoundWhoseQuestionTheMasterAnswersToTheAnswerNotTheReview() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .message("outcome=question — override the gate?").build();

        boolean reviewable = MasterReviewJob.reviewable(asking, false, true);

        assertThat(reviewable).isFalse();
    }

    @Test
    void handsAPlanNobodyReadYetToTheMaster() {
        TaskState planned = TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).message("plan written").build();

        boolean reviewable = MasterReviewJob.reviewable(planned, false, true);

        assertThat(reviewable).isTrue();
    }
}
