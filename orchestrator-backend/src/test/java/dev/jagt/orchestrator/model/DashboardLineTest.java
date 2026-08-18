package dev.jagt.orchestrator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardLineTest {

    @Test
    void showsNoDetailWhileInDevelopment() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS).message("some progress note").build();

        assertThat(DashboardLine.forTask(task)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"CI_POLLING", "REVIEWED", "APPROVED", "DEPLOYED", "REVERTED"})
    void showsTheRequestLinkForEveryStatusThatHasOneOut(TaskStatus status) {
        TaskState task = TaskState.builder("p", "/wt", status).mrUrl("https://gitlab/x/-/merge_requests/9").build();

        assertThat(DashboardLine.forTask(task)).isEqualTo("https://gitlab/x/-/merge_requests/9");
    }

    @Test
    void saysTheLinkIsMissingWhenAReviewedTaskHasNone() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEWED).build();

        assertThat(DashboardLine.forTask(task)).isEqualTo("review request link missing");
    }

    @Test
    void shoutsInCapsWhenPipelineFailed() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_FAILED)
                .message("trigger_commons failed").mrUrl("https://mr").build();

        assertThat(DashboardLine.forTask(task)).startsWith("PROBLEM: ").contains("trigger_commons");
    }

    @Test
    void flagsThatADeployConflictNeedsTheHuman() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .message("resolve conflict in /repos/ABC-1-deploy").build();

        assertThat(DashboardLine.forTask(task)).startsWith("NEEDS YOU: ").contains("ABC-1-deploy");
    }

    @Test
    void shoutsNeedsInputWhenAgentIsBlocked() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: FE or BE decision").build();

        assertThat(DashboardLine.forTask(task)).isEqualTo("NEEDS INPUT: FE or BE decision");
    }

    @Test
    void showsNoDetailWhenNothingHasBeenReportedYet() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.NEW).build();

        assertThat(DashboardLine.forTask(task)).isEmpty();
    }

    @Test
    void showsNoDetailWhileTheShipIsInFlight() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.SHIPPING).message("shipping").build();

        assertThat(DashboardLine.forTask(task)).isEmpty();
    }

    @Test
    void showsTheMrLinkWhenReviewPendingHasOne() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING).mrUrl("https://host/mr/417").build();

        assertThat(DashboardLine.forTask(task)).isEqualTo("https://host/mr/417");
    }

    @Test
    void showsNoDetailWhenReviewPendingHasNoRequestYet() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING).build();

        assertThat(DashboardLine.forTask(task)).isEmpty();
    }

    @Test
    void shoutsNeedsInputRatherThanTheMrLinkWhenAReviewRoundEndedInAQuestion() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
                .message("awaiting: cache or index?").mrUrl("https://host/mr/425").build();

        assertThat(DashboardLine.forTask(task)).isEqualTo("NEEDS INPUT: cache or index?");
    }

    @Test
    void saysTheRoundChangedNothingWithoutDroppingTheLinkToTheThreadsItNames() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
                .message("no changes: every comment already handled").mrUrl("https://host/mr/440").build();

        assertThat(DashboardLine.forTask(task))
                .isEqualTo("ANSWERED: every comment already handled · https://host/mr/440");
    }

}
