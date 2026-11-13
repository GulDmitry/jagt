package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestProjectConfigBuilder.projectConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class AgentStatusReportsTest {

  private final LinkRefusals refusals = mock(LinkRefusals.class);
  private final WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
  private final ConfigService configService = mock(ConfigService.class);
  private final Checkers checkers = new Checkers(new TaskChart(Composition.builtIn()));

  private final ConfigService.ConfigFile.MasterConfig actMaster =
      new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
  private final ConfigService.ConfigFile verifying =
      ConfigService.ConfigFile.defaults()
          .withProjects(
              Map.of(
                  "proj",
                  projectConfig().withVerifyCommand(List.of("./gradlew", "test")).build()));

  private final TaskState inProgress =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();
  private final TaskState polling =
      TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
          .alias("a1")
          .mrUrl("http://mr/1")
          .build();
  private final ConfigService.ConfigFile projOnly =
      ConfigService.ConfigFile.defaults()
          .withProjects(
              Map.of(
                  "proj",
                  new dev.jagt.orchestrator.task.ProjectConfig(
                      "/repo", "origin/main", "dev", List.of())));
  private final ConfigService.ConfigFile withRegistry =
      projOnly.withProjects(
          Map.of(
              "proj",
              new dev.jagt.orchestrator.task.ProjectConfig(
                  "/repo", "origin/main", "dev", List.of()),
              "registry",
              new dev.jagt.orchestrator.task.ProjectConfig(
                  "/reg", "origin/main", "dev", List.of())));

  @TempDir Path root;

  private StateService state;
  private FlowReports flowReports;
  private HandBack handBack;
  private AgentStatusReports reports;
  private TaskState planning;
  private ThrowingCallable planReport;

  @BeforeEach
  void wireReports() {
    state = TestStateServiceBuilder.stateService().withRoot(root).build();
    flowReports =
        new FlowReports(
            state,
            Clock.systemUTC(),
            checkers,
            new FlowRules(new TaskChart(Composition.builtIn())));
    handBack =
        new HandBack(
            worktreeChanges,
            new Rounds(configService, ignored -> false, () -> false, () -> false, checkers),
            configService);
    reports = new AgentStatusReports(state, refusals, flowReports, handBack, configService);
    planning = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
    planReport = () -> reports.report(TaskStatus.PLAN_PENDING, "plan ready", "ABC-1");
  }

  @Test
  void tellsTheSessionItsHandBackIsWaitingOnVerificationRatherThanOnTheHuman() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(verifying);

    String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

    assertThat(answer).contains("VERIFYING");
  }

  @Test
  void recordsOnTheSessionThatTheTurnReportedWhenReportLands() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.IN_PROGRESS, "going on", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().session().reportedAt()).isPositive();
  }

  @Test
  void handsGreenVerificationToReviewRatherThanVerifyingItAgain() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).alias("a1").build());
    when(configService.load()).thenReturn(verifying);

    reports.verified(true, "verified", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @Test
  @ResourceLock(Resources.GLOBAL)
  void reportsAnErrorWhenVerdictJagtMeasuredDoesNotLand() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVERTED).alias("a1").build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    ((Logger) LoggerFactory.getLogger(AgentStatusReports.class)).addAppender(log);

    reports.verified(true, "verified", "ABC-1");

    assertThat(List.copyOf(log.list))
        .filteredOn(event -> "verdict not landed".equals(event.getMessage()))
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.ERROR);
  }

  @Test
  void answersReportOnVanishedTaskAsFactNotFieldToFix() {
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(() -> reports.report(TaskStatus.IN_PROGRESS, "working", "ABC-9"))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.NO_SUCH_TASK));
  }

  @Test
  void tellsTheSessionWhereItsReportLandedInTheStateItWasWrittenTo() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVERTED).alias("a1").build());
    StateService staleView = spy(state);
    doReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()))
        .when(staleView)
        .task("ABC-1");
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    AgentStatusReports reports =
        new AgentStatusReports(staleView, refusals, flowReports, handBack, configService);

    String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

    assertThat(answer)
        .startsWith("Task ABC-1 stays REVERTED")
        .endsWith("Your line was recorded (done)");
  }

  @Test
  void tellsTheSessionTheMasterReadsItsHandBackNext() {
    state.putTask("ABC-1", inProgress);
    when(configService.load())
        .thenReturn(ConfigService.ConfigFile.defaults().withMaster(actMaster));

    String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

    assertThat(answer)
        .isEqualTo(
            "Task ABC-1 -> REVIEW_PENDING (done); the Master reads this round next;"
                + " end your turn");
  }

  @Test
  void storesTheRequestLinkTheAgentPutInItsStatusMessage() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.REVIEWED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(state.task("ABC-1").orElseThrow().message()).isEqualTo("deployed");
  }

  @Test
  void takesTheRequestLinkFromTheArgumentRatherThanFromTheProse() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

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
    assertThat(reported.history())
        .extracting(StatusChange::status)
        .containsOnlyOnce(TaskStatus.CI_POLLING);
  }

  @Test
  void refusesRequestReportedUnderProjectTheTaskDoesNotHold() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(
            () ->
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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

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
    assertThat(reported.mrCreatedAt()).isGreaterThan(1_000L);
    assertThat(reported.lastPolledAt()).isZero();
  }

  @Test
  void leavesTheStatusAndRecordsNoRoundWhenTheOnlyLinkIsForeign() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repos(List.of(new TaskRepo("proj", "/wt", "git@host:team/proj.git", null)))
            .build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING", "shipped", null, "https://host/other/proj/-/merge_requests/9", Map.of()),
        "ABC-1");

    TaskState reported = state.task("ABC-1").orElseThrow();
    assertThat(reported.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(reported.mrUrl()).isNull();
    assertThat(reported.mrCreatedAt()).isZero();
  }

  @Test
  void putsForeignLinkOnTheBoardsHeadWhenThePathIntoReportsIsNotSessions() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repos(List.of(new TaskRepo("proj", "/wt", "git@host:team/proj.git", null)))
            .build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    ReachLedger reach = new ReachLedger();
    AgentStatusReports reports =
        new AgentStatusReports(state, new LinkRefusals(
                reach,
                (task, line) -> {
                  throw new AssertionError("no session to tell");
                },
                state), flowReports, handBack, configService);

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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(
            () ->
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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

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
    assertThat(reported.status()).isEqualTo(TaskStatus.CI_POLLING);
    assertThat(reported.reviewRequestOf("proj")).isEmpty();
    assertThat(reported.reviewRequestOf("web"))
        .contains("https://host/team/web/-/merge_requests/3");
    assertThat(reported.mrCreatedAt()).isPositive();
  }

  @Test
  void readsTheOutcomeFromTheArgumentWhenTheMessageCarriesNoMarker() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS", "which cache should this use", "question", null, Map.of()),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().report()).isEqualTo(AgentReport.QUESTION);
  }

  @Test
  void recordsRoundWithDiffWhenTheWorktreeContradictsNoChangesClaim() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host/mr/1")
            .build());
    when(worktreeChanges.anyUncommitted(any())).thenReturn(true);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage("REVIEW_PENDING", "already handled", "no_changes", null, Map.of()),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().report()).isEqualTo(AgentReport.PLAIN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"no_changes", "no-changes", "no changes"})
  void keepsNoChangesRoundThatTheWorktreeBearsOut(String outcome) {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host/mr/1")
            .build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage("REVIEW_PENDING", "already handled", outcome, null, Map.of()),
        "ABC-1");

    TaskState after = state.task("ABC-1").orElseThrow();
    assertThat(after.report()).isEqualTo(AgentReport.NO_CHANGES);
    assertThat(after.message()).isEqualTo("already handled");
  }

  @Test
  void marksQuestionStoppedOnDeniedCallAsPermissionOnlyTheHumanGrants() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS", "edits in the deploy worktree denied", "permission", null, Map.of()),
        "ABC-1");

    TaskState after = state.task("ABC-1").orElseThrow();
    assertThat(after.report()).isEqualTo(AgentReport.PERMISSION);
    assertThat(after.message()).isEqualTo("edits in the deploy worktree denied");
  }

  @ParameterizedTest
  @ValueSource(strings = {"branch pushed", "pushed, see the http docs for the request"})
  void refusesToSayTaskIsWaitingOnChecksWithoutNamingTheRequest(
      String message) {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(() -> reports.report(TaskStatus.CI_POLLING, message, "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required with CI_POLLING");
  }

  @Test
  void letsTaskSayItIsWaitingOnTheChecksOnceItNamesTheRequest() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(
        TaskStatus.CI_POLLING, "MR: https://gitlab.example/g/p/-/merge_requests/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_POLLING);
  }

  @Test
  void refusesTheStatusEvenForTaskThatAlreadyCarriesRequestLink() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .alias("a1")
            .mrUrl("https://gitlab.example/g/p/-/merge_requests/1")
            .build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(
            () -> reports.report(TaskStatus.CI_POLLING, "waiting for the pipeline", "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required with CI_POLLING");
  }

  @Test
  void truncatesStatusMessageToOneDashboardLineWhenAgentSendsAnEssay() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.IN_PROGRESS, "root cause\nanalysis ".repeat(20), "ABC-1");

    String stored = state.task("ABC-1").orElseThrow().message();
    assertThat(stored).hasSizeLessThanOrEqualTo(100).doesNotContain("\n").endsWith("…");
  }

  @Test
  void keepsTheWholeRequestLinkWhenTheMessageIsTooLongForOneDashboardLine() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    String link = "https://gitlab.example/group/subgroup/team/project/-/merge_requests/1234567";
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "pipeline queued after the push — MR: " + link, "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo(link);
  }

  @Test
  void advancesRoundTheReviewApprovedToApproved() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.APPROVED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.APPROVED);
  }

  @Test
  void advancesCleanUnapprovedRoundToReviewed() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.REVIEWED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEWED);
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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.REVIEWED, Optional.empty());

    TaskState after = state.task("ABC-1").orElseThrow();
    assertThat(after.message()).isEqualTo("squash or keep the commits?");
    assertThat(after.lastActiveTimestamp()).isEqualTo(1_700_000_000_000L);
    assertThat(after.session().quietSince()).isEqualTo(1_700_000_000_000L);
  }

  @Test
  void stampsThePollingWindowWhenRequestIsFirstLinked() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isPositive();
  }

  @Test
  void startsFreshPollingWindowForEachRoundHandedBackOnTheSameRequest() {
    long lastRound = System.currentTimeMillis() - Duration.ofHours(25).toMillis();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
            .alias("a1")
            .mrUrl("http://mr/1")
            .mrCreatedAt(lastRound)
            .lastPolledAt(lastRound)
            .build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isGreaterThan(lastRound);
  }

  @Test
  void startsFreshWindowWhenTheAgentNamesAnotherRequestWithoutLeavingCiPolling() {
    long lastRound = System.currentTimeMillis() - Duration.ofHours(25).toMillis();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("http://mr/1")
            .mrCreatedAt(lastRound)
            .build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/2", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isGreaterThan(lastRound);
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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/2", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().stopped()).isFalse();
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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().pipelineStatus()).isNull();
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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(worktreeChanges.pushedTips(any())).thenReturn("proj=aaa");

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().approvedRead()).isTrue();
    assertThat(state.task("ABC-1").orElseThrow().checksRead()).isEqualTo("success");
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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(worktreeChanges.pushedTips(any())).thenReturn("proj=bbb");

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().checksRead()).isNull();
    assertThat(state.task("ABC-1").orElseThrow().approvedRead()).isNull();
  }

  @Test
  void refusesPlanWithoutAcceptanceCriteria() throws IOException {
    Files.writeString(root.resolve("plan.md"), "# Plan\n1. change the guide\n");
    state.putTask("ABC-1", planning);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(planReport)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("## Acceptance criteria");
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.IN_PROGRESS);
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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isEqualTo(12345L);
  }

  @Test
  void refusesToPullTaskTheReviewHasPassedBackIntoWaitingOnChecks() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.APPROVED)
            .alias("a1")
            .mrUrl("https://host/mr/1")
            .build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(
            () ->
                reports.report(TaskStatus.CI_POLLING, "review request: https://host/mr/1", "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already APPROVED");
    assertThat(state.task("ABC-1"))
        .get()
        .extracting(TaskState::status)
        .isEqualTo(TaskStatus.APPROVED);
  }

  @Test
  void putsRedRoundOnTheBoard() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.CI_FAILED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_FAILED);
  }

  @Test
  void saysRedRoundWasRelayedToTheSessionWhenItWas() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.markRead("ABC-1", TaskStatus.CI_FAILED, Optional.empty());

    assertThat(state.task("ABC-1").orElseThrow().message())
        .isEqualTo("checks failed — relayed to the session");
  }

  @Test
  void saysRedRoundStoppedTheTaskInsteadOfRelayingItWhenJagtStoppedIt() {
    state.putTask("ABC-1", polling);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    TaskStop stop =
        new TaskStop(TaskStop.Trigger.INFRA, 3_000, "unit-tests: runner_system_failure, 3 retries");

    reports.markRead("ABC-1", TaskStatus.CI_FAILED, Optional.of(stop));

    assertThat(state.task("ABC-1").orElseThrow().message())
        .doesNotContain("relayed")
        .contains("unit-tests: runner_system_failure, 3 retries");
  }

  @Test
  void tellsTheSessionItsHandBackIsVerifiedBeforeTheMasterReadsIt() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(verifying.withMaster(actMaster));

    String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

    assertThat(answer)
        .endsWith(
            "verification runs first, and the Master reads this round once it passes;"
                + " end your turn");
  }

  @Test
  void recordsTheProbesPlanDeclaresAsItIsPutUp() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        """
        # Plan
        ## Acceptance criteria
        - [here] guide exists
        ```probes
        artifact docs/guide.md
        ```
        """);
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.PLAN_PENDING, "plan ready", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().probes())
        .containsExactly(Probe.specified(Probe.Kind.ARTIFACT, List.of("docs/guide.md"), 0));
  }

  @Test
  void refusesPlanWhoseProbesBlockHoldsLineNoProbeReads() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        """
        ## Acceptance criteria
        - [here] builds
        ```probes
        command ./gradlew test | tee out.log
        ```
        """);
    state.putTask("ABC-1", planning);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(planReport)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("| tee out.log");
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.IN_PROGRESS);
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
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "CI_POLLING",
            "still waiting",
            null,
            null,
            Map.of("proj", "https://host/proj/mr/9", "web", "https://host/web/mr/3")),
        "ABC-1");

    TaskState reported = state.task("ABC-1").orElseThrow();
    assertThat(reported.mrCreatedAt()).isEqualTo(1_000L);
    assertThat(reported.lastPolledAt()).isEqualTo(9_000L);
  }

  @Test
  void opensThePlanGateWhenAnAutoplanSessionReportsThatTheTaskNeedsNoPlan() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.NEW).planGate(PlanGate.CLOSED).build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS", "one file, no contradiction", "no_plan", null, Map.of()),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void keepsThePlanGateWhenSessionOpenedInPlanModeReportsThatTheTaskNeedsNoPlan() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.NEW).planGate(PlanGate.REQUIRED).build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(
            () ->
                reports.report(
                    new AgentStatusMessage("IN_PROGRESS", "one file", "no_plan", null, Map.of()),
                    "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outcome");
    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void recordsTheProjectQuestionNeedsOnTheTask() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(withRegistry);

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS",
            "needs the registry",
            "question",
            new AgentStatusMessage.Request(null, Map.of()),
            null,
            "registry",
            null,
            Map.of()),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().repoRequest()).isEqualTo("registry");
  }

  @Test
  void refusesProjectNoConfigurationHasAndRecordsNothing() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(projOnly);

    assertThatThrownBy(
            () ->
                reports.report(
                    new AgentStatusMessage(
                        "IN_PROGRESS",
                        "needs it",
                        "question",
                        new AgentStatusMessage.Request(null, Map.of()),
                        null,
                        "ghost",
                        null,
                        Map.of()),
                    "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needsProject")
        .hasMessageContaining("[proj]");
    assertThat(state.task("ABC-1").orElseThrow().repoRequest()).isNull();
  }

  @Test
  void answersSessionsReportWithTheStatusItLandedOnAndItsOwnWords() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    String answer =
        reports.report(
            new AgentStatusMessage("IN_PROGRESS", "working", null, null, Map.of()), "ABC-1");

    assertThat(answer).isEqualTo("Task ABC-1 -> IN_PROGRESS (working)");
  }

  @Test
  void answersVerificationVerdictWithTheStatusItLanded() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    String answer = reports.verified(true, "checks green", "ABC-1");

    assertThat(answer).startsWith("Task ABC-1 -> REVIEW_PENDING (checks green)");
  }

  @Test
  void closesTheTurnSessionsReportAnswersAndLeavesOpenTheOneJagtsOwnReportDoesNot() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .turn(Turn.none().relayed("round", "d1", "", 100))
            .build();
    state.putTask("ABC-1", working);
    state.putTask("ABC-2", working);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(new AgentStatusMessage("IN_PROGRESS", "working", null, null, Map.of()), "ABC-1");
    reports.report(TaskStatus.IN_PROGRESS, "working", "ABC-2");

    assertThat(state.task("ABC-1").orElseThrow().turnOrNone().open()).isFalse();
    assertThat(state.task("ABC-2").orElseThrow().turnOrNone().open()).isTrue();
  }

  @Test
  @ResourceLock(Resources.GLOBAL)
  void logsTheMoveReportMadeWhereTheStatusChanged() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    ((Logger) LoggerFactory.getLogger(AgentStatusReports.class)).addAppender(log);

    reports.report(TaskStatus.IN_PROGRESS, "started", "ABC-1");

    assertThat(List.copyOf(log.list))
        .extracting(ILoggingEvent::getMessage)
        .containsExactly("agent status changed");
    assertThat(log.list.getFirst().getKeyValuePairs())
        .extracting(pair -> pair.key + "=" + pair.value)
        .contains("status=IN_PROGRESS", "previous=NEW", "said=started");
  }

  @Test
  @ResourceLock(Resources.GLOBAL)
  void logsQuestionOnceWhenTheStatusStaysAndNothingWhenItIsRepeatedOrPlain() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    ((Logger) LoggerFactory.getLogger(AgentStatusReports.class)).addAppender(log);

    reports.report(new AgentStatusMessage("IN_PROGRESS", "working", null, null, Map.of()), "ABC-1");
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "which cache", "question", null, Map.of()), "ABC-1");
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "which cache", "question", null, Map.of()), "ABC-1");

    assertThat(List.copyOf(log.list))
        .extracting(ILoggingEvent::getMessage)
        .containsExactly("agent asked");
  }

  @Test
  @ResourceLock(Resources.GLOBAL)
  void logsLineHeldOffTheCardButNotQuestionAskedAgainWhileHeld() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVERTED).alias("a1").build());
    state.putTask(
        "ABC-2",
        TaskState.builder("proj", "/wt", TaskStatus.REVERTED)
            .alias("a2")
            .report(AgentReport.QUESTION)
            .build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    ((Logger) LoggerFactory.getLogger(AgentStatusReports.class)).addAppender(log);

    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "back at it", null, null, Map.of()), "ABC-1");
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "which one", "question", null, Map.of()), "ABC-2");

    assertThat(List.copyOf(log.list))
        .extracting(ILoggingEvent::getMessage)
        .containsExactly("agent line held off the card");
    assertThat(log.list.getFirst().getKeyValuePairs())
        .extracting(pair -> pair.key + "=" + pair.value)
        .contains("task=ABC-1");
  }

  @Test
  void dropsTheRequestWhenTheSessionReportsAnythingElse() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repoRequest("registry")
            .build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(TaskStatus.IN_PROGRESS, "back to work", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().repoRequest()).isNull();
  }

  @Test
  void owesJudgementOnEverySwitchThatIsOnToSessionStillInNew() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withAgent(
                    ConfigService.ConfigFile.AgentConfig.defaults()
                        .withVisualize(new ConfigService.ConfigFile.VisualizeConfig(true))));

    MessageContext context = reports.sessionContextFor("ABC-1");
    assertThat(context.switchesOn()).containsExactly("visualize");
    assertThat(context.firstReport()).isTrue();
  }

  @Test
  void knowsNoSwitchIsOnWhenNoneIs() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThat(reports.sessionContextFor("ABC-1").switchesOn()).isEmpty();
  }

  @Test
  void marksTheFirstReportOnlyWhileTheTaskIsNew() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withAgent(
                    ConfigService.ConfigFile.AgentConfig.defaults()
                        .withVisualize(new ConfigService.ConfigFile.VisualizeConfig(true))));

    assertThat(reports.sessionContextFor("ABC-1").firstReport()).isFalse();
  }

  @Test
  void missesThePictureWhileTheWorktreeHoldsNoTaskVisual() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThat(reports.sessionContextFor("ABC-1").visualMissing()).isTrue();
  }

  @Test
  void missesNoPictureOnceTheWorktreeHoldsTheTaskVisual() throws IOException {
    Files.writeString(root.resolve("task_visual.md"), "picture");
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThat(reports.sessionContextFor("ABC-1").visualMissing()).isFalse();
  }

  @Test
  void missesNoPictureOfTaskWithoutWorktree() {
    state.putTask("ABC-1", TaskState.builder("proj", "", TaskStatus.IN_PROGRESS).build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThat(reports.sessionContextFor("ABC-1").visualMissing()).isFalse();
  }

  @Test
  void recordsTheJudgementsReportCarriesOnTheTask() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS",
            "working",
            null,
            new AgentStatusMessage.Request(null, Map.of()),
            null,
            null,
            null,
            Map.of("visualize", "no — config-only change")),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().switches())
        .containsExactly(Map.entry("visualize", "no — config-only change"));
  }
}
