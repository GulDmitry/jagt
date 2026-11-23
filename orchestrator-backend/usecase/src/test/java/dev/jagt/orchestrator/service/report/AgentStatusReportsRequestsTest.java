package dev.jagt.orchestrator.service.report;

import static dev.jagt.orchestrator.service.report.TestAgentStatusReportsBuilder.agentStatusReports;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TestStateServiceBuilder;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.service.session.LinkRefusals;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgentStatusReportsRequestsTest {

  private final LinkRefusals refusals = mock(LinkRefusals.class);
  private final WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
  private final ConfigService configService = mock(ConfigService.class);

  private final TaskState inProgress =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();

  private final TaskState polling =
      TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
          .alias("a1")
          .mrUrl("http://mr/1")
          .build();

  @TempDir Path root;

  private StateService state;
  private AgentStatusReports reports;

  @BeforeEach
  void wireReports() {
    state = TestStateServiceBuilder.stateService().withRoot(root).build();
    reports = agentStatusReports().build(state, refusals, worktreeChanges, configService);
  }

  @Test
  void storesTheRequestLinkTheAgentPutInItsStatusMessage() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: https://gitlab/x/-/merge_requests/9", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().mrUrl())
        .isEqualTo("https://gitlab/x/-/merge_requests/9");
  }

  @Test
  void leavesDeployedTaskAloneWhenPolledRoundKeepsCallingItReviewed() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .alias("a1")
            .message("deployed")
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.REVIEWED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.DEPLOYED);
    assertThat(state.task("ABC-1").orElseThrow().standing().message()).isEqualTo("deployed");
  }

  @Test
  void takesTheRequestLinkFromTheArgumentRatherThanFromTheProse() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING", "handed over", null, "https://gitlab/x/-/merge_requests/9", Map.of()),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().mrUrl())
        .isEqualTo("https://gitlab/x/-/merge_requests/9");
  }

  @Test
  void linksEveryRepositoryToItsOwnRequestAndRecordsOneRound() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .repos(
                List.of(
                    new TaskRepo("proj", "/wt", "git@host:proj.git", null),
                    new TaskRepo("web", "/wt-web", "git@host:web.git", null)))
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING",
            "requests up",
            null,
            null,
            Map.of(
                "proj",
                "https://host/proj/-/merge_requests/9",
                "web",
                "https://host/web/-/merge_requests/3")),
        "ABC-1");

    TaskState reported = state.task("ABC-1").orElseThrow();
    assertThat(reported.repos())
        .extracting(TaskRepo::mrUrl)
        .containsExactly(
            "https://host/proj/-/merge_requests/9", "https://host/web/-/merge_requests/3");
    assertThat(reported.standing().history())
        .extracting(StatusChange::status)
        .containsOnlyOnce(TaskStatus.CI_POLLING);
  }

  @Test
  void refusesRequestReportedUnderProjectTheTaskDoesNotHold() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(() ->
            reports.report(
                new AgentStatusMessage(
                    "CI_POLLING",
                    "requests up",
                    null,
                    null,
                    Map.of("frontend", "https://host/x/-/merge_requests/9")),
                "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("project 'frontend' is not on this task")
        .hasMessageContaining("proj");
  }

  @Test
  void armsFreshRoundWhenSecondRepositoryFinallyOpensItsRequest() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .repos(
                List.of(
                    new TaskRepo("proj", "/wt", "git@host:proj.git", "https://host/proj/mr/9"),
                    new TaskRepo("web", "/wt-web", "git@host:web.git", null)))
            .mrCreatedAt(1_000L)
            .lastPolledAt(9_000L)
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING",
            "web is up too",
            null,
            null,
            Map.of("proj", "https://host/proj/mr/9", "web", "https://host/web/mr/3")),
        "ABC-1");

    TaskState reported = state.task("ABC-1").orElseThrow();
    assertThat(reported.reviewRequestOf("web")).contains("https://host/web/mr/3");
    assertThat(reported.round().mrCreatedAt()).isGreaterThan(1_000L);
    assertThat(reported.round().lastPolledAt()).isZero();
  }

  @Test
  void leavesTheStatusAndRecordsNoRoundWhenTheOnlyLinkIsForeign() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repos(List.of(new TaskRepo("proj", "/wt", "git@host:team/proj.git", null)))
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING", "shipped", null, "https://host/other/proj/-/merge_requests/9", Map.of()),
        "ABC-1");

    TaskState reported = state.task("ABC-1").orElseThrow();
    assertThat(reported.standing().status()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(reported.mrUrl()).isNull();
    assertThat(reported.round().mrCreatedAt()).isZero();
  }

  @Test
  void putsForeignLinkOnTheBoardsHeadWhenThePathIntoReportsIsNotSessions() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repos(List.of(new TaskRepo("proj", "/wt", "git@host:team/proj.git", null)))
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());
    ReachLedger reach = new ReachLedger();
    AgentStatusReports reports =
        agentStatusReports()
            .build(
                state,
                new LinkRefusals(
                    reach,
                    (task, line) -> {
                      throw new AssertionError("no session to tell");
                    },
                    state),
                worktreeChanges,
                configService);

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING", "shipped", null, "https://host/other/proj/-/merge_requests/9", Map.of()),
        "ABC-1");

    assertThat(reach.standing())
        .extracting(ReachLost::said)
        .containsExactly("a1 proj: request is not under team/proj");
  }

  @Test
  void refusesShipWhoseOnlyLinkIsForeignFromTaskThatCannotHoldItsStatus() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.NEW)
            .alias("a1")
            .repos(List.of(new TaskRepo("proj", "/wt", "git@host:team/proj.git", null)))
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(() ->
            reports.report(
                new AgentStatusMessage(
                    "CI_POLLING",
                    "shipped",
                    null,
                    "https://host/other/proj/-/merge_requests/9",
                    Map.of()),
                "ABC-1"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("not under the repository's remote");
  }

  @Test
  void dropsOnlyTheForeignLinkOfShipAndRecordsTheGoodOne() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repos(
                List.of(
                    new TaskRepo("proj", "/wt", "git@host:team/proj.git", null),
                    new TaskRepo("web", "/wt-web", "git@host:team/web.git", null)))
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING",
            "shipped",
            null,
            null,
            Map.of(
                "proj",
                "https://host/other/proj/-/merge_requests/9",
                "web",
                "https://host/team/web/-/merge_requests/3")),
        "ABC-1");

    TaskState reported = state.task("ABC-1").orElseThrow();
    assertThat(reported.standing().status()).isEqualTo(TaskStatus.CI_POLLING);
    assertThat(reported.reviewRequestOf("proj")).isEmpty();
    assertThat(reported.reviewRequestOf("web"))
        .contains("https://host/team/web/-/merge_requests/3");
    assertThat(reported.round().mrCreatedAt()).isPositive();
  }

  @ParameterizedTest
  @ValueSource(strings = {"branch pushed", "pushed, see the http docs for the request"})
  void refusesToSayTaskIsWaitingOnChecksWithoutNamingTheRequest(String message) {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(() -> reports.report(TaskStatus.CI_POLLING, message, "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required with CI_POLLING");
  }

  @Test
  void letsTaskSayItIsWaitingOnTheChecksOnceItNamesTheRequest() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        TaskStatus.CI_POLLING, "MR: https://gitlab.example/g/p/-/merge_requests/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.CI_POLLING);
  }

  @Test
  void refusesTheStatusEvenForTaskThatAlreadyCarriesRequestLink() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .alias("a1")
            .mrUrl("https://gitlab.example/g/p/-/merge_requests/1")
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(
            () -> reports.report(TaskStatus.CI_POLLING, "waiting for the pipeline", "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required with CI_POLLING");
  }

  @Test
  void keepsTheWholeRequestLinkWhenTheMessageIsTooLongForOneDashboardLine() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    String link = "https://gitlab.example/group/subgroup/team/project/-/merge_requests/1234567";
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "pipeline queued after the push — MR: " + link, "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo(link);
  }

  @Test
  void advancesRoundTheReviewApprovedToApproved() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.APPROVED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.APPROVED);
  }

  @Test
  void advancesCleanUnapprovedRoundToReviewed() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.REVIEWED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.REVIEWED);
  }

  @Test
  void writesNothingWhenPollReadsTheOutcomeTheTaskAlreadyHolds() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .alias("a1")
            .mrUrl("http://mr/1")
            .message("squash or keep the commits?")
            .report(AgentReport.QUESTION)
            .lastActiveTimestamp(1_700_000_000_000L)
            .session(Session.defaults().withQuietSince(1_700_000_000_000L))
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.REVIEWED, Optional.empty());

    TaskState after = state.task("ABC-1").orElseThrow();
    assertThat(after.standing().message()).isEqualTo("squash or keep the commits?");
    assertThat(after.standing().lastActiveTimestamp()).isEqualTo(1_700_000_000_000L);
    assertThat(after.work().session().quietSince()).isEqualTo(1_700_000_000_000L);
  }

  @Test
  void stampsThePollingWindowWhenRequestIsFirstLinked() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().round().mrCreatedAt()).isPositive();
  }

  @Test
  void startsFreshPollingWindowForEachRoundHandedBackOnTheSameRequest() {
    long lastRound = new FakeClock().millis() - Duration.ofHours(25).toMillis();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
            .alias("a1")
            .mrUrl("http://mr/1")
            .mrCreatedAt(lastRound)
            .lastPolledAt(lastRound)
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().round().mrCreatedAt()).isGreaterThan(lastRound);
  }

  @Test
  void startsFreshWindowWhenTheAgentNamesAnotherRequestWithoutLeavingCiPolling() {
    long lastRound = new FakeClock().millis() - Duration.ofHours(25).toMillis();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("http://mr/1")
            .mrCreatedAt(lastRound)
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/2", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().round().mrCreatedAt()).isGreaterThan(lastRound);
  }

  @Test
  void liftsStopCountedOnTheRequestTheSessionReportsAnotherOneInPlaceOf() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("http://mr/1")
            .stop(new TaskStop(TaskStop.Trigger.SHIPS, 1_000, "4 ships", "http://mr/1", null))
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/2", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().work().stopped()).isFalse();
  }

  @Test
  void dropsThePreviousRoundsChecksVerdictWhenRoundGoesBackOutForReview() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
            .alias("a1")
            .mrUrl("http://mr/1")
            .mrCreatedAt(12345L)
            .pipelineStatus("failed")
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().checks().pipelineStatus()).isNull();
  }

  @Test
  void keepsTheRecordedApprovalAndChecksWhenTheSessionReportsAgainOnTheSameCommit() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("http://mr/1")
            .headTip("proj=aaa")
            .checksTip("proj=aaa")
            .pipelineStatus("success")
            .approved(true)
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());
    when(worktreeChanges.pushedTips(any())).thenReturn("proj=aaa");

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().approvedRead()).isTrue();
    assertThat(state.task("ABC-1").orElseThrow().checks().read()).isEqualTo("success");
  }

  @Test
  void hidesTheRecordedChecksWhenTheSessionReportsAgainAfterPushingAnotherCommit() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("http://mr/1")
            .headTip("proj=aaa")
            .checksTip("proj=aaa")
            .pipelineStatus("failed")
            .approved(true)
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());
    when(worktreeChanges.pushedTips(any())).thenReturn("proj=bbb");

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().checks().read()).isNull();
    assertThat(state.task("ABC-1").orElseThrow().approvedRead()).isNull();
  }

  @Test
  void keepsThePollingWindowWhileTheAgentRepeatsThatItIsWaitingOnTheSameChecks() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("http://mr/1")
            .mrCreatedAt(12345L)
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().round().mrCreatedAt()).isEqualTo(12345L);
  }

  @Test
  void refusesToPullTaskTheReviewHasPassedBackIntoWaitingOnChecks() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.APPROVED)
            .alias("a1")
            .mrUrl("https://host/mr/1")
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(() ->
            reports.report(TaskStatus.CI_POLLING, "review request: https://host/mr/1", "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already APPROVED");
    assertThat(state.task("ABC-1"))
        .get()
        .extracting(taskState -> taskState.standing().status())
        .isEqualTo(TaskStatus.APPROVED);
  }

  @Test
  void putsRedRoundOnTheBoard() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.CI_FAILED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.CI_FAILED);
  }

  @Test
  void saysRedRoundWasRelayedToTheSessionWhenItWas() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.CI_FAILED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().standing().message())
        .isEqualTo("checks failed — relayed to the session");
  }

  @Test
  void saysRedRoundStoppedTheTaskInsteadOfRelayingItWhenJagtStoppedIt() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigFile.defaults());
    TaskStop stop =
        new TaskStop(TaskStop.Trigger.INFRA, 3_000, "unit-tests: runner_system_failure, 3 retries");

    reports.markRead("ABC-1", TaskStatus.CI_FAILED, Optional.of(stop));

    assertThat(state.task("ABC-1").orElseThrow().standing().message())
        .doesNotContain("relayed")
        .contains("unit-tests: runner_system_failure, 3 retries");
  }

  @Test
  void keepsThePollingWindowWhileTheAgentRepeatsEveryRepositorysRequest() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .repos(
                List.of(
                    new TaskRepo("proj", "/wt", "git@host:proj.git", "https://host/proj/mr/9"),
                    new TaskRepo("web", "/wt-web", "git@host:web.git", "https://host/web/mr/3")))
            .mrCreatedAt(1_000L)
            .lastPolledAt(9_000L)
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING",
            "still waiting",
            null,
            null,
            Map.of("proj", "https://host/proj/mr/9", "web", "https://host/web/mr/3")),
        "ABC-1");

    TaskState reported = state.task("ABC-1").orElseThrow();
    assertThat(reported.round().mrCreatedAt()).isEqualTo(1_000L);
    assertThat(reported.round().lastPolledAt()).isEqualTo(9_000L);
  }
}
