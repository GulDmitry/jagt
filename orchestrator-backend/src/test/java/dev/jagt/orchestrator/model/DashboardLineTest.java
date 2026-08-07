package dev.jagt.orchestrator.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardLineTest {

    @Test
    void showsNoDetailWhileInDevelopment() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
                .message("some progress note").alias("a1").title("Some ticket title").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).isEmpty();
    }

    @Test
    void showsMrLinkWhileInReview() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_POLLING)
                .message("MR: https://gitlab/x/-/merge_requests/9").alias("a1").title("title").mrUrl("https://gitlab/x/-/merge_requests/9").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).isEqualTo("https://gitlab/x/-/merge_requests/9");
    }

    @Test
    void showsMrLinkWhenReviewedAndReadyToDeploy() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEWED)
                .message("reviewed").alias("a1").title("title").mrUrl("https://gitlab/x/-/merge_requests/9").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).isEqualTo("https://gitlab/x/-/merge_requests/9");
    }

    @Test
    void shoutsInCapsWhenPipelineFailed() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_FAILED)
                .message("trigger_commons failed").alias("a1").title("title").mrUrl("https://mr").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).startsWith("PROBLEM: ").contains("trigger_commons");
    }

    @Test
    void flagsThatADeployConflictNeedsTheHuman() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .message("resolve conflict in /repos/ABC-1-deploy").alias("a1").title("title").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).startsWith("NEEDS YOU: ").contains("ABC-1-deploy");
    }

    @Test
    void shoutsNeedsInputWhenAgentIsBlocked() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: FE or BE decision").alias("a1").title("title").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).isEqualTo("NEEDS INPUT: FE or BE decision");
    }

    @Test
    void showsNoDetailForAFreshTask() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.NEW).alias("a1").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).isEmpty();
    }

    @Test
    void showsShippingWhileTheAgentPushes() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.SHIPPING).message("shipping").alias("a1").title("title").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).startsWith("SHIPPING");
    }

    @Test
    void showsTheMrLinkWhenReviewPendingHasOne() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("some title").mrUrl("https://host/mr/417").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).isEqualTo("https://host/mr/417");
    }

    @Test
    void showsTheMrLinkForAReviewPendingTaskEvenWhenTheAgentLeftAnAwaitingNote() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
                .message("awaiting: review comments; branch resumed, MR open").alias("a1").title("title").mrUrl("https://host/mr/425").build();

        assertThat(DashboardLine.forTask("ABC-1", task)).isEqualTo("https://host/mr/425");
    }
}
