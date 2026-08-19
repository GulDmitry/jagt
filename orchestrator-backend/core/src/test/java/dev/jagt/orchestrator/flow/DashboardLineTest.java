package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardLineTest {

    @ParameterizedTest
    @CsvSource(nullValues = "SILENT", value = {"IN_PROGRESS,some progress note", "SHIPPING,shipping",
            "NEW,SILENT", "REVIEW_PENDING,SILENT"})
    void showsNoDetailWhileThereIsNothingForAHumanToActOn(TaskStatus status, String message) {
        TaskState task = TaskState.builder("p", "/wt", status).message(message).build();

        assertThat(DashboardLine.forTask(task)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEW_PENDING", "CI_POLLING", "REVIEWED", "APPROVED",
            "DEPLOYED", "REVERTED"})
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

    /** The one case where the status itself lies: it reads as work in progress and nothing is progressing. */
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "SHIPPING"})
    void shoutsThatAnAgentTheWatchdogFoundSilentIsNowTheHumansProblem(TaskStatus status) {
        TaskState task = TaskState.builder("p", "/wt", status).message("step 2").silentSince(1_000).build();

        assertThat(DashboardLine.forTask(task))
                .isEqualTo("NEEDS YOU: agent silent — no report and a quiet window");
    }

    /** What it asked is worth more than the fact it then stopped; the next-move line still names the silence. */
    @Test
    void quotesTheQuestionRatherThanTheSilenceWhenTheAgentAskedBeforeItWentQuiet() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: FE or BE decision").silentSince(1_000).build();

        assertThat(DashboardLine.forTask(task)).isEqualTo("NEEDS INPUT: FE or BE decision");
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

    @Test
    void shoutsThatTheChecksWentRedBeforeShowingTheRequestLink() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/mr/501").pipelineStatus("failed").build();

        assertThat(DashboardLine.forTask(task)).isEqualTo("CHECKS RED · https://host/mr/501");
    }

    @Test
    void saysTheChecksAreStillRunningWhileTheHostHasNoAnswerYet() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/mr/502").pipelineStatus("running").build();

        assertThat(DashboardLine.forTask(task)).isEqualTo("checks running · https://host/mr/502");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "UNREAD", value = {"success", "UNREAD"})
    void showsOnlyTheRequestLinkWhenTheChecksHaveNothingToAdd(String pipelineStatus) {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/mr/503").pipelineStatus(pipelineStatus).build();

        assertThat(DashboardLine.forTask(task)).isEqualTo("https://host/mr/503");
    }
}
