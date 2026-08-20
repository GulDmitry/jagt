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

        assertThat(DashboardLine.forTask(task, null)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEW_PENDING", "CI_POLLING", "REVIEWED", "APPROVED",
            "DEPLOYED", "REVERTED"})
    void saysNothingAboutARequestEverySurfaceCanLinkToItself(TaskStatus status) {
        TaskState task = TaskState.builder("p", "/wt", status).mrUrl("https://gitlab/x/-/merge_requests/9").build();

        assertThat(DashboardLine.forTask(task, "https://gitlab/x/-/merge_requests/9")).isEmpty();
    }

    @Test
    void saysSoWhenTheTaskHasARequestNothingCanLinkTo() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEWED)
                .mrUrl("javascript:alert(1)").build();

        assertThat(DashboardLine.forTask(task, null))
                .isEqualTo("PROBLEM: review request link unusable: javascript:alert(1)");
    }

    @Test
    void saysNothingAboutARequestAReviewedTaskDoesNotHaveYet() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEWED).build();

        assertThat(DashboardLine.forTask(task, null)).isEmpty();
    }

    @Test
    void shoutsInCapsWhenPipelineFailed() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_FAILED)
                .message("trigger_commons failed").mrUrl("https://mr").build();

        assertThat(DashboardLine.forTask(task, "https://mr")).startsWith("PROBLEM: ").contains("trigger_commons");
    }

    @Test
    void flagsThatADeployConflictNeedsTheHuman() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .message("resolve conflict in /repos/ABC-1-deploy").build();

        assertThat(DashboardLine.forTask(task, null)).startsWith("NEEDS YOU: ").contains("ABC-1-deploy");
    }

    @Test
    void shoutsNeedsInputWhenAgentIsBlocked() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: FE or BE decision").build();

        assertThat(DashboardLine.forTask(task, null)).isEqualTo("NEEDS INPUT: FE or BE decision");
    }

    /** The one case where the status itself lies: it reads as work in progress and nothing is progressing. */
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "SHIPPING"})
    void shoutsThatAnAgentTheWatchdogFoundSilentIsNowTheHumansProblem(TaskStatus status) {
        TaskState task = TaskState.builder("p", "/wt", status).message("step 2").silentSince(1_000).build();

        assertThat(DashboardLine.forTask(task, null))
                .isEqualTo("NEEDS YOU: agent stopped: no MCP call and no process in its window");
    }

    /** What it asked is worth more than the fact it then stopped; the next-move line still names the silence. */
    @Test
    void quotesTheQuestionRatherThanTheSilenceWhenTheAgentAskedBeforeItWentQuiet() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: FE or BE decision").silentSince(1_000).build();

        assertThat(DashboardLine.forTask(task, null)).isEqualTo("NEEDS INPUT: FE or BE decision");
    }

    @Test
    void shoutsNeedsInputRatherThanTheMrLinkWhenAReviewRoundEndedInAQuestion() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
                .message("awaiting: cache or index?").mrUrl("https://host/mr/425").build();

        assertThat(DashboardLine.forTask(task, "https://host/mr/425")).isEqualTo("NEEDS INPUT: cache or index?");
    }

    @Test
    void saysTheRoundChangedNothing() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
                .message("no changes: every comment already handled").mrUrl("https://host/mr/440").build();

        assertThat(DashboardLine.forTask(task, "https://host/mr/440"))
                .isEqualTo("ANSWERED: every comment already handled");
    }

    @ParameterizedTest
    @CsvSource({"failed", "running", "success"})
    void leavesTheChecksToWhicheverSurfaceIsShowingThem(String pipelineStatus) {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/mr/501").pipelineStatus(pipelineStatus).build();

        assertThat(DashboardLine.forTask(task, "https://host/mr/501")).isEmpty();
    }
}
