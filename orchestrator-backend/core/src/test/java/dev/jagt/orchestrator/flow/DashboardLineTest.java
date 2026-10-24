package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.ConflictWatch;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardLineTest {

    @ParameterizedTest
    @CsvSource(nullValues = "SILENT", value = {"IN_PROGRESS,some progress note", "SHIPPING,shipping",
            "NEW,SILENT", "REVIEW_PENDING,SILENT"})
    void showsNoDetailWhileThereIsNothingForAHumanToActOn(TaskStatus status, String message) {
        TaskState task = TaskState.builder("p", "/wt", status).message(message).build();

        assertThat(DashboardLine.forTask(task, null).text()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEW_PENDING", "CI_POLLING", "REVIEWED", "APPROVED",
            "DEPLOYED", "REVERTED"})
    void saysNothingAboutARequestEverySurfaceCanLinkToItself(TaskStatus status) {
        TaskState task = TaskState.builder("p", "/wt", status).mrUrl("https://gitlab/x/-/merge_requests/9").build();

        assertThat(DashboardLine.forTask(task, "https://gitlab/x/-/merge_requests/9").text()).isEmpty();
    }

    @Test
    void saysSoWhenTheTaskHasARequestNothingCanLinkTo() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEWED)
                .mrUrl("javascript:alert(1)").build();

        assertThat(DashboardLine.forTask(task, null).text())
                .isEqualTo("PROBLEM: review request link unusable: javascript:alert(1)");
    }

    @Test
    void saysNothingAboutARequestAReviewedTaskDoesNotHaveYet() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEWED).build();

        assertThat(DashboardLine.forTask(task, null).text()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {"CI_FAILED,checks red,PLAIN,PROBLEM", "DEPLOY_CONFLICT,-,PLAIN,YOURS",
            "IN_PROGRESS,FE or BE,QUESTION,YOURS", "REVIEW_PENDING,all handled,NO_CHANGES,NOTE",
            "IN_PROGRESS,step 2,PLAIN,NONE"})
    void namesWhatKindOfLineItIsSoNoReaderParsesItsWords(TaskStatus status, String message, AgentReport report,
                                                         DashboardLine.Kind kind) {
        TaskState task = TaskState.builder("p", "/wt", status).message(message).report(report).build();

        assertThat(DashboardLine.forTask(task, null).kind()).isEqualTo(kind);
    }

    @Test
    void shoutsInCapsWhenPipelineFailed() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_FAILED)
                .message("trigger_commons failed").mrUrl("https://mr").build();

        assertThat(DashboardLine.forTask(task, "https://mr").text()).startsWith("PROBLEM: ").contains("trigger_commons");
    }

    @Test
    void flagsThatADeployConflictNeedsTheHuman() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .message("resolve conflict in /repos/ABC-1-deploy").build();

        assertThat(DashboardLine.forTask(task, null).text()).startsWith("NEEDS YOU: ").contains("ABC-1-deploy");
    }

    @Test
    void saysWhereAndSinceWhenADeployConflictStoppedMoving() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.DEPLOY_CONFLICT).lastActiveTimestamp(1_000)
                .message("resolve conflict in /src/ABC-42-deploy")
                .conflict(ConflictWatch.handed(1_000, "/src/ABC-42-deploy", new ConflictWatch.Progress(7, 0, "d"), 1_000)
                        .toldAt(1_201_000))
                .build();

        assertThat(DashboardLine.forTask(task, null).text()).isEqualTo("NEEDS YOU: the deploy conflict stopped moving:"
                + " 7 paths unmerged, 0 staged in /src/ABC-42-deploy, unchanged since 1970-01-01T00:00:00Z");
    }

    @Test
    void shoutsNeedsInputWhenAgentIsBlocked() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
                .message("FE or BE decision").report(AgentReport.QUESTION).build();

        assertThat(DashboardLine.forTask(task, null).text()).isEqualTo("NEEDS INPUT: FE or BE decision");
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"IN_PROGRESS", "SHIPPING"})
    void shoutsThatAnAgentTheWatchdogFoundSilentIsNowTheHumansProblem(TaskStatus status) {
        TaskState task = TaskState.builder("p", "/wt", status).message("step 2").silentSince(1_000)
                .silentBecause("waiting for input").build();

        assertThat(DashboardLine.forTask(task, null).text())
                .isEqualTo("NEEDS YOU: agent stopped: waiting for input");
    }

    @Test
    void saysWhatToLookAtWhenNothingWasEverReported() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.NEW).silentSince(1_000).build();

        assertThat(DashboardLine.forTask(task, null).text())
                .isEqualTo("NEEDS YOU: agent stopped: the agent never reported — check that the CLI started");
    }

    @Test
    void quotesWhatTheSessionReportedEvenBeforeItsFirstStatus() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.NEW).silentSince(1_000)
                .silentBecause("waiting for input").build();

        assertThat(DashboardLine.forTask(task, null).text())
                .isEqualTo("NEEDS YOU: agent stopped: waiting for input");
    }

    @Test
    void fallsBackToAGeneralSentenceWhereNoReasonWasRecorded() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS).silentSince(1_000).build();

        assertThat(DashboardLine.forTask(task, null).text())
                .isEqualTo("NEEDS YOU: agent stopped: nothing has moved in its session");
    }

    @Test
    void quotesTheQuestionRatherThanTheSilenceWhenTheAgentAskedBeforeItWentQuiet() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
                .message("FE or BE decision").report(AgentReport.QUESTION).silentSince(1_000).build();

        assertThat(DashboardLine.forTask(task, null).text()).isEqualTo("NEEDS INPUT: FE or BE decision");
    }

    @Test
    void shoutsNeedsInputRatherThanTheMrLinkWhenAReviewRoundEndedInAQuestion() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
                .message("cache or index?").report(AgentReport.QUESTION).mrUrl("https://host/mr/425").build();

        assertThat(DashboardLine.forTask(task, "https://host/mr/425").text()).isEqualTo("NEEDS INPUT: cache or index?");
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"CI_POLLING", "REVIEWED", "APPROVED"})
    void shoutsNeedsInputForAQuestionAskedFromAStatusThatWaitsOnSomebodyElse(TaskStatus status) {
        TaskState task = TaskState.builder("p", "/wt", status)
                .message("cache or index?").report(AgentReport.QUESTION).mrUrl("https://host/mr/425").build();

        assertThat(DashboardLine.forTask(task, "https://host/mr/425").text()).isEqualTo("NEEDS INPUT: cache or index?");
    }

    @Test
    void stillSaysWhoIsWaitedOnWhenTheQuestionCarriesNoWordsOfItsOwn() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
                .report(AgentReport.QUESTION).mrUrl("https://host/mr/441").build();

        assertThat(DashboardLine.forTask(task, "https://host/mr/441").text())
                .isEqualTo("NEEDS INPUT: the agent is waiting on you");
    }

    @Test
    void saysTheRoundChangedNothing() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
                .message("every comment already handled").report(AgentReport.NO_CHANGES).mrUrl("https://host/mr/440").build();

        assertThat(DashboardLine.forTask(task, "https://host/mr/440").text())
                .isEqualTo("ANSWERED: every comment already handled"
                        + " — the open threads are the reviewer's to close");
    }

    @ParameterizedTest
    @CsvSource({"failed", "running", "success"})
    void leavesTheChecksToWhicheverSurfaceIsShowingThem(String pipelineStatus) {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/mr/501").pipelineStatus(pipelineStatus).build();

        assertThat(DashboardLine.forTask(task, "https://host/mr/501").text()).isEmpty();
    }

    @Test
    void namesWhatGitNowObeysAsTheHumansMove() {
        TaskState task = TaskState.builder("p", "/wt", TaskStatus.APPROVED)
                .gitTrust(GitTrust.of(Map.of()).withChanged("core.fsmonitor in /r/.git/config added")).build();

        assertThat(DashboardLine.forTask(task, null))
                .isEqualTo(new DashboardLine.Line(DashboardLine.Kind.YOURS,
                        "NEEDS YOU: core.fsmonitor in /r/.git/config added"));
    }
}
