package dev.jawo.orchestrator.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardLineTest {

    @Test
    void showsNoDetailWhileInDevelopment() {
        TaskState task = new TaskState("p", "/wt", TaskStatus.IN_PROGRESS, 0,
                "some progress note", "a1", null, "Some ticket title", null);

        assertThat(DashboardLine.forTask("ABC-1", task)).isEmpty();
    }

    @Test
    void showsMrLinkWhileInReview() {
        TaskState task = new TaskState("p", "/wt", TaskStatus.CI_POLLING, 0,
                "MR: https://gitlab/x/-/merge_requests/9", "a1", null, "title", "https://gitlab/x/-/merge_requests/9");

        assertThat(DashboardLine.forTask("ABC-1", task)).isEqualTo("https://gitlab/x/-/merge_requests/9");
    }

    @Test
    void shoutsInCapsWhenPipelineFailed() {
        TaskState task = new TaskState("p", "/wt", TaskStatus.CI_FAILED, 0,
                "trigger_commons failed", "a1", null, "title", "https://mr");

        assertThat(DashboardLine.forTask("ABC-1", task)).startsWith("PROBLEM: ").contains("trigger_commons");
    }

    @Test
    void shoutsNeedsInputWhenAgentIsBlocked() {
        TaskState task = new TaskState("p", "/wt", TaskStatus.IN_PROGRESS, 0,
                "awaiting: FE or BE decision", "a1", null, "title", null);

        assertThat(DashboardLine.forTask("ABC-1", task)).isEqualTo("NEEDS INPUT: FE or BE decision");
    }

    @Test
    void showsNoDetailForAFreshTask() {
        TaskState task = new TaskState("p", "/wt", TaskStatus.NEW, 0, null, "a1", null, null, null);

        assertThat(DashboardLine.forTask("ABC-1", task)).isEmpty();
    }

    @Test
    void showsShippingWhileTheAgentPushes() {
        TaskState task = new TaskState("p", "/wt", TaskStatus.SHIPPING, 0, "shipping", "a1", null, "title", null);

        assertThat(DashboardLine.forTask("ABC-1", task)).startsWith("SHIPPING");
    }

    @Test
    void showsTheMrLinkWhenReviewPendingHasOne() {
        TaskState task = new TaskState("p", "/wt", TaskStatus.REVIEW_PENDING, 0,
                null, "a1", null, "some title", "https://host/mr/417");

        assertThat(DashboardLine.forTask("ABC-1", task)).isEqualTo("https://host/mr/417");
    }
}
