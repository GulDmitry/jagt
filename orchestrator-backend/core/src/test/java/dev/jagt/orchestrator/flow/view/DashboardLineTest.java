package dev.jagt.orchestrator.flow.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class DashboardLineTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @ParameterizedTest
  @CsvSource(
      nullValues = "SILENT",
      value = {
        "IN_PROGRESS,some progress note",
        "SHIPPING,shipping",
        "NEW,SILENT",
        "REVIEW_PENDING,SILENT"
      })
  void showsNoDetailWhileThereIsNothingForHumanToActOn(TaskStatus status, String message) {
    TaskState task = TaskState.builder("p", "/wt", status).message(message).build();

    assertThat(DashboardLine.forTask(task, null, null).text()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEW_PENDING", "CI_POLLING", "REVIEWED", "APPROVED", "DEPLOYED", "REVERTED"})
  void saysNothingAboutRequestEverySurfaceCanLinkToItself(TaskStatus status) {
    TaskState task =
        TaskState.builder("p", "/wt", status).mrUrl("https://gitlab/x/-/merge_requests/9").build();

    assertThat(DashboardLine.forTask(task, "https://gitlab/x/-/merge_requests/9", null).text())
        .isEmpty();
  }

  @Test
  void saysSoWhenTheTaskHasRequestNothingCanLinkTo() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.REVIEWED).mrUrl("javascript:alert(1)").build();

    assertThat(DashboardLine.forTask(task, null, null).text())
        .isEqualTo("PROBLEM: review request link unusable: javascript:alert(1)");
  }

  @Test
  void saysNothingAboutRequestReviewedTaskDoesNotHaveYet() {
    TaskState task = TaskState.builder("p", "/wt", TaskStatus.REVIEWED).build();

    assertThat(DashboardLine.forTask(task, null, null).text()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "-",
      value = {
        "CI_FAILED,checks red,PLAIN,PROBLEM",
        "DEPLOY_CONFLICT,-,PLAIN,YOURS",
        "IN_PROGRESS,FE or BE,QUESTION,YOURS",
        "REVIEW_PENDING,all handled,NO_CHANGES,NOTE",
        "IN_PROGRESS,step 2,PLAIN,NONE"
      })
  void namesWhatKindOfLineItIsSoNoReaderParsesItsWords(
      TaskStatus status, String message, AgentReport report, DashboardLine.Kind kind) {
    TaskState task = TaskState.builder("p", "/wt", status).message(message).report(report).build();

    assertThat(DashboardLine.forTask(task, null, null).kind()).isEqualTo(kind);
  }

  @Test
  void shoutsInCapsWhenPipelineFailed() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.CI_FAILED)
            .message("trigger_commons failed")
            .mrUrl("https://mr")
            .build();

    assertThat(DashboardLine.forTask(task, "https://mr", null).text())
        .startsWith("PROBLEM: ")
        .contains("trigger_commons");
  }

  @Test
  void flagsThatDeployConflictNeedsTheHuman() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .message("resolve conflict in /repos/ABC-1-deploy")
            .build();

    assertThat(DashboardLine.forTask(task, null, null).text())
        .startsWith("NEEDS YOU: ")
        .contains("ABC-1-deploy");
  }

  @Test
  void saysWhereAndSinceWhenDeployConflictStoppedMoving() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .lastActiveTimestamp(1_000)
            .message("resolve conflict in /src/ABC-42-deploy")
            .build();
    TaskState opened =
        reducer.deployStepped(
            task, new DeployStep.Opened(List.of(Deployment.Target.of("p", "dev"))), 1_000);
    TaskState conflicted =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("p", Deployment.Cause.MERGE), 1_000);
    TaskState seen =
        reducer.deployStepped(
            conflicted, new DeployStep.Seen(new Conflict.Progress(7, 0, "d")), 1_000);
    TaskState stalled =
        reducer.deployStepped(
            seen,
            new DeployStep.Told(
                "7 paths unmerged, 0 staged in "
                    + "/src/ABC-42-deploy, unchanged since 1970-01-01T00:00:00Z"),
            1_201_000);

    assertThat(DashboardLine.forTask(stalled, null, null).text())
        .isEqualTo(
            "NEEDS YOU: the deploy conflict stopped moving: 7 paths unmerged, 0 staged in"
                + " /src/ABC-42-deploy, unchanged since 1970-01-01T00:00:00Z");
  }

  @Test
  void dropsTheStalledConflictLineWhereTheConflictedRepositoryLeftTheTask() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                    new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta")),
                TaskStatus.DEPLOY_CONFLICT)
            .lastActiveTimestamp(1_000)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task,
            new DeployStep.Opened(
                List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
            1_000);
    TaskState merged =
        reducer.deployStepped(
            reducer.deployStepped(opened, new DeployStep.Merged("alpha", "a1"), 1_000),
            new DeployStep.Merged("beta", "b1"),
            1_000);
    TaskState red =
        reducer.deployStepped(
            merged, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_000);
    TaskState seen =
        reducer.deployStepped(red, new DeployStep.Seen(new Conflict.Progress(7, 0, "d")), 1_000);
    TaskState stalled = reducer.deployStepped(seen, new DeployStep.Told("pipeline red"), 1_201_000);
    assertThat(DashboardLine.forTask(stalled, null, null).text()).contains("stopped moving");

    TaskState left = reducer.repoLeft(stalled, "beta", 1_300_000);

    assertThat(DashboardLine.forTask(left, null, null).text()).doesNotContain("stopped moving");
  }

  @Test
  void shoutsNeedsInputWhenAgentIsBlocked() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
            .message("FE or BE decision")
            .report(AgentReport.QUESTION)
            .build();

    assertThat(DashboardLine.forTask(task, null, null).text())
        .isEqualTo("NEEDS INPUT: FE or BE decision");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "SHIPPING"})
  void shoutsThatAnAgentTheWatchdogFoundSilentIsNowTheHumansProblem(TaskStatus status) {
    TaskState task =
        TaskState.builder("p", "/wt", status)
            .message("step 2")
            .session(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.GONE, 1_000, "waiting for input")))
            .build();

    assertThat(DashboardLine.forTask(task, null, null).text())
        .isEqualTo("NEEDS YOU: session silent: the session ended: waiting for input");
  }

  @Test
  void saysWhatToLookAtWhenNothingWasEverReported() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.NEW)
            .session(Session.defaults().withQuietSince(1_000))
            .build();

    assertThat(DashboardLine.forTask(task, null, null).text())
        .isEqualTo(
            "NEEDS YOU: session silent: the agent never reported — check that the CLI started");
  }

  @Test
  void quotesWhatTheSessionReportedEvenBeforeItsFirstStatus() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.NEW)
            .session(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.GONE, 1_000, "waiting for input")))
            .build();

    assertThat(DashboardLine.forTask(task, null, null).text())
        .isEqualTo("NEEDS YOU: session silent: the session ended: waiting for input");
  }

  @Test
  void fallsBackToGeneralSentenceWhereNoReasonWasRecorded() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
            .session(Session.defaults().withQuietSince(1_000))
            .build();

    assertThat(DashboardLine.forTask(task, null, null).text())
        .isEqualTo("NEEDS YOU: session silent: nothing has moved in its session");
  }

  @Test
  void quotesTheQuestionRatherThanTheSilenceWhenTheAgentAskedBeforeItWentQuiet() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.IN_PROGRESS)
            .message("FE or BE decision")
            .report(AgentReport.QUESTION)
            .session(Session.defaults().withQuietSince(1_000))
            .build();

    assertThat(DashboardLine.forTask(task, null, null).text())
        .isEqualTo("NEEDS INPUT: FE or BE decision");
  }

  @Test
  void shoutsNeedsInputRatherThanTheMrLinkWhenReviewRoundEndedInQuestion() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
            .message("cache or index?")
            .report(AgentReport.QUESTION)
            .mrUrl("https://host/mr/425")
            .build();

    assertThat(DashboardLine.forTask(task, "https://host/mr/425", null).text())
        .isEqualTo("NEEDS INPUT: cache or index?");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"CI_POLLING", "REVIEWED", "APPROVED"})
  void shoutsNeedsInputForQuestionAskedFromStatusThatWaitsOnSomebodyElse(TaskStatus status) {
    TaskState task =
        TaskState.builder("p", "/wt", status)
            .message("cache or index?")
            .report(AgentReport.QUESTION)
            .mrUrl("https://host/mr/425")
            .build();

    assertThat(DashboardLine.forTask(task, "https://host/mr/425", null).text())
        .isEqualTo("NEEDS INPUT: cache or index?");
  }

  @Test
  void stillSaysWhoIsWaitedOnWhenTheQuestionCarriesNoWordsOfItsOwn() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
            .report(AgentReport.QUESTION)
            .mrUrl("https://host/mr/441")
            .build();

    assertThat(DashboardLine.forTask(task, "https://host/mr/441", null).text())
        .isEqualTo("NEEDS INPUT: the agent is waiting on you");
  }

  @Test
  void saysTheRoundChangedNothing() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.REVIEW_PENDING)
            .message("every comment already handled")
            .report(AgentReport.NO_CHANGES)
            .mrUrl("https://host/mr/440")
            .build();

    assertThat(DashboardLine.forTask(task, "https://host/mr/440", null).text())
        .isEqualTo(
            "ANSWERED: every comment already handled"
                + " — the open threads are the reviewer's to close");
  }

  @ParameterizedTest
  @CsvSource({"failed", "running", "success"})
  void leavesTheChecksToWhicheverSurfaceIsShowingThem(String pipelineStatus) {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("https://host/mr/501")
            .pipelineStatus(pipelineStatus)
            .build();

    assertThat(DashboardLine.forTask(task, "https://host/mr/501", null).text()).isEmpty();
  }

  @Test
  void namesWhatGitNowObeysAsTheHumansMove() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.APPROVED)
            .gitTrust(GitTrust.of(Map.of()).withChanged("core.fsmonitor in /r/.git/config added"))
            .build();

    assertThat(DashboardLine.forTask(task, null, null))
        .isEqualTo(
            new DashboardLine.Line(
                DashboardLine.Kind.YOURS, "NEEDS YOU: core.fsmonitor in /r/.git/config added"));
  }

  @Test
  void namesTheRevertWhenStalledConflictIsRevertsOne() {
    TaskState task =
        TaskState.builder("p", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .lastActiveTimestamp(1_000)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task, new DeployStep.Opened(List.of(Deployment.Target.of("p", "dev"))), 1_000);
    TaskState conflicted =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("p", Deployment.Cause.REVERT), 1_000);
    TaskState seen =
        reducer.deployStepped(
            conflicted, new DeployStep.Seen(new Conflict.Progress(7, 0, "d")), 1_000);
    TaskState stalled =
        reducer.deployStepped(seen, new DeployStep.Told("7 paths unmerged"), 1_201_000);

    assertThat(DashboardLine.forTask(stalled, null, null).text())
        .isEqualTo("NEEDS YOU: the revert conflict stopped moving: 7 paths unmerged");
  }

  @Test
  void notesWhyNoSessionStartsWhereCredentialFails() {
    TaskState task = TaskState.builder("p", "/wt", TaskStatus.NEW).build();

    DashboardLine.Line line = DashboardLine.forTask(task, null, "credential check 'npm' fails");

    assertThat(line.kind()).isEqualTo(DashboardLine.Kind.NOTE);
    assertThat(line.text()).contains("npm");
  }
}
