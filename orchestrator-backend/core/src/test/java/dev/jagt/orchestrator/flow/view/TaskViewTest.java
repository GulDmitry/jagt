package dev.jagt.orchestrator.flow.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.review.AutoReviewWatch;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.session.Wake;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.RoleRecord;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.task.usage.ModelTier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class TaskViewTest {

  private final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  private final TaskView workingTask =
      TaskView.of(
          rules,
          "ABC-1",
          TaskState.builder("api", "/api-wt", TaskStatus.IN_PROGRESS).alias("a1").build(),
          RoundState.of(AgentReport.PLAIN, false),
          AutoReviewWatch.none(),
          Map.of("api", "dev"));

  private final Deployment apiLandedWebWaiting =
      new Deployment(
          1,
          List.of(
              Deployment.Target.of("api", "dev").withMerge("abc1234"),
              Deployment.Target.of("web", "dev")),
          1,
          null);

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {});

  private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> watches() {
    return java.util.stream.Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(AutoReviewWatch.watching(1L), Owner.CI),
        org.junit.jupiter.params.provider.Arguments.of(AutoReviewWatch.none(), Owner.CI),
        org.junit.jupiter.params.provider.Arguments.of(
            AutoReviewWatch.windowElapsed(24), Owner.YOU),
        org.junit.jupiter.params.provider.Arguments.of(AutoReviewWatch.offForTask(), Owner.YOU),
        org.junit.jupiter.params.provider.Arguments.of(AutoReviewWatch.noRound(), Owner.YOU));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.MethodSource("watches")
  void asksForHumanOnlyWhereAnExpectedPollHasStopped(AutoReviewWatch watch, Owner expected) {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://host/mr/1")
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            watch,
            Map.of("proj", "dev"));

    assertThat(view.owner()).isEqualTo(expected);
  }

  @Test
  void namesTheModelSessionRunsOnWhileItIsTierUp() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .build()
                .withTier(new ModelTier(List.of("sonnet", "opus"), 1)),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.identity().tier()).isEqualTo("opus");
  }

  @Test
  void namesNoModelForSessionOnItsOwn() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .build()
                .withTier(new ModelTier(List.of("sonnet", "opus"), 0)),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.identity().tier()).isNull();
  }

  @Test
  void servesTheTaskAsOneFlatObjectWithoutTheDraftedRepliesFlag() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build(),
            RoundState.of(AgentReport.PLAIN, true),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    var json = new JsonMapper().readTree(new JsonMapper().writeValueAsString(view));

    assertThat(json.propertyNames())
        .containsExactlyInAnyOrder(
            "id",
            "alias",
            "project",
            "title",
            "tier",
            "status",
            "statusLabel",
            "phase",
            "owner",
            "attention",
            "ask",
            "hint",
            "actions",
            "confirmations",
            "detail",
            "detailKind",
            "ticketUrl",
            "reviewRequestUrl",
            "repos",
            "statusSince",
            "requestOpenedAt",
            "history",
            "deployed",
            "autoReview",
            "pipeline",
            "pipelineSaid",
            "pipelineUnread",
            "approved",
            "switches",
            "stop",
            "waiting",
            "repliesWrittenAt");
  }

  @Test
  void servesEachActionAsOneFlatObject() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    var json = new JsonMapper().readTree(new JsonMapper().writeValueAsString(view));

    assertThat(json.get("actions").get(0).propertyNames())
        .containsExactlyInAnyOrder(
            "id",
            "label",
            "hint",
            "primary",
            "group",
            "readOnly",
            "again",
            "onRow",
            "apart",
            "key");
  }

  @Test
  void asksYouToAnswerTheSessionWhenItsHookReportedBlockingPromptWhileItsReportIsPlain() {
    TaskState working = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState asked =
        reducer
            .signed(
                working,
                new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK),
                1_000)
            .withSession(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 500, null))
                    .withQuietSince(500));

    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            asked,
            RoundState.of(asked.standing().report(), false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.owner()).isEqualTo(Owner.YOU);
    assertThat(view.ask()).isEqualTo("answer the session");
    assertThat(view.detail()).doesNotContain("agent stopped");
  }

  @Test
  void drawsNoButtonForRepositoryRemovalWhichIsOnlyTyped() {
    TaskState several =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt"), TaskRepo.of("web", "/wt2")),
                TaskStatus.IN_PROGRESS)
            .build();

    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            several,
            RoundState.of(several.standing().report(), false),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.actions()).extracting(TaskView.ActionView::id).doesNotContain("remove-repo");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "javascript:fetch('/api/tasks/a1/actions/deploy',{method:'POST'})",
        "JavaScript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "file:///etc/passwd",
        "not a url at all",
      })
  void dropsLinkThatIsNotHttpRatherThanHandingItToAnAnchor(String hostile) {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .ticketUrl(hostile)
                .mrUrl(hostile)
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.ticketUrl()).isNull();
    assertThat(view.reviewRequestUrl()).isNull();
  }

  @Test
  void keepsRealLinksAndTrimsTheWhitespaceAroundThem() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .ticketUrl("  https://tracker.example.com/browse/ABC-1  ")
                .mrUrl("http://host.example.com/x/-/merge_requests/9")
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.ticketUrl()).isEqualTo("https://tracker.example.com/browse/ABC-1");
    assertThat(view.reviewRequestUrl()).isEqualTo("http://host.example.com/x/-/merge_requests/9");
  }

  @Test
  void leavesBothLinksUnsetWhenTheTaskHasNone() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.ticketUrl()).isNull();
    assertThat(view.reviewRequestUrl()).isNull();
  }

  @Test
  void stillSaysSomethingWhenTheStoredRequestLinkWasUnusable() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("javascript:alert(1)")
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.detail())
        .isEqualTo("PROBLEM: review request link unusable: javascript:alert(1)");
  }

  @Test
  void carriesEveryRepositoryWithItsOwnRequestSoNoSurfaceHasToGuessWhichDiffIsWhich() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder(
                    List.of(
                        new TaskRepo(
                            "api",
                            "/api-wt",
                            "git@host:g/api.git",
                            "https://host/api/-/merge_requests/1"),
                        new TaskRepo(
                            "web",
                            "/web-wt",
                            "git@host:g/web.git",
                            "https://host/web/-/merge_requests/2")),
                    TaskStatus.CI_POLLING)
                .alias("a1")
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("api", "dev", "web", "dev"));

    assertThat(view.repos())
        .extracting(TaskView.RepoView::project, TaskView.RepoView::reviewRequestUrl)
        .containsExactly(
            tuple("api", "https://host/api/-/merge_requests/1"),
            tuple("web", "https://host/web/-/merge_requests/2"));
    assertThat(view.project()).isEqualTo("api");
  }

  @Test
  void doneStandsApartFromTheOtherVerbs() {
    assertThat(workingTask.actions())
        .filteredOn(TaskView.ActionView::apart)
        .extracting(TaskView.ActionView::id)
        .containsExactly("done");
  }

  @Test
  void doneCarriesNoKey() {
    assertThat(workingTask.actions())
        .filteredOn(action -> action.id().equals("done"))
        .singleElement()
        .extracting(TaskView.ActionView::key)
        .isEqualTo("");
  }

  @Test
  void shipCarriesItsLetter() {
    assertThat(workingTask.actions())
        .filteredOn(action -> action.id().equals("ship"))
        .singleElement()
        .extracting(TaskView.ActionView::key)
        .isEqualTo("s");
  }

  @Test
  void highlightsNoVerbWhileTheAgentHoldsTheMove() {
    assertThat(workingTask.actions()).noneMatch(TaskView.ActionView::primary);
  }

  @Test
  void highlightsTheMovesVerbWhereTheMoveIsTheHumans() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("api", "/api-wt", TaskStatus.CI_FAILED)
                .alias("a1")
                .mrUrl("http://host/mr/1")
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.actions())
        .filteredOn(TaskView.ActionView::primary)
        .extracting(TaskView.ActionView::id)
        .containsExactly("sweep");
  }

  @Test
  void theHistorySaysEachStatusInTheBoardsWords() {
    TaskState started = TaskState.builder("api", "/api-wt", TaskStatus.NEW).alias("a1").build();
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            started.withStatus(TaskStatus.IN_PROGRESS, "working", 2_000),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.history()).extracting(TaskView.Step::label).endsWith("agent working");
  }

  @Test
  void onlyFocusRidesOnTheRowOfWorkingTask() {
    assertThat(workingTask.actions())
        .filteredOn(TaskView.ActionView::onRow)
        .extracting(TaskView.ActionView::id)
        .containsExactly("focus");
  }

  @Test
  void offersSweepAsSoonAsAnyRepositoryHasRequestOpen() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder(
                    List.of(
                        TaskRepo.of("api", "/api-wt").withRemoteUrl("git@host:g/api.git"),
                        new TaskRepo(
                            "web",
                            "/web-wt",
                            "git@host:g/web.git",
                            "https://host/web/-/merge_requests/2")),
                    TaskStatus.CI_POLLING)
                .alias("a1")
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("api", "dev", "web", "dev"));

    assertThat(view.actions()).extracting(TaskView.ActionView::id).contains("sweep");
    assertThat(view.actions())
        .filteredOn(action -> action.id().equals("sweep"))
        .singleElement()
        .extracting(TaskView.ActionView::group)
        .isEqualTo("flow");
  }

  @Test
  void marksTaskWhoseWorkIsOnSharedBranchAfterTheDeployStatusHasMovedOn() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .alias("a1")
                .deployment(
                    new Deployment(
                        1,
                        List.of(Deployment.Target.of("proj", "dev").withMerge("abc1234")),
                        1,
                        null))
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.deployed()).isTrue();
  }

  @Test
  void namesTheCheckerThatPassedTheLiveDeployInTheStatus() {
    TaskState passed =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .alias("a1")
            .deployment(
                new Deployment(
                    1, List.of(Deployment.Target.of("proj", "dev").withMerge("abc1234")), 1, null))
            .build()
            .withRole("acceptance", RoleRecord.defaults().withPassed("proj@abc1234"));

    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            passed,
            RoundState.of(passed.standing().report(), false)
                .withCheckers(new Checkers(new TaskChart(Composition.builtIn())).of(passed)),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.statusLabel()).isEqualTo("accepted");
  }

  @Test
  void takesTheMarkOffOnceTheRevertHasForgottenTheMergeCommit() {
    TaskState reverted =
        TaskState.builder("proj", "/wt", TaskStatus.REVERTED)
            .alias("a1")
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("proj", "dev")
                            .withMerge("abc1234")
                            .withReverted("def5678")),
                    1,
                    null))
            .build();

    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            reverted,
            RoundState.of(reverted.standing().report(), false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.deployed()).isFalse();
  }

  @Test
  void marksTheDeployVerbAsRepeatWhileItsLastRunIsStillLive() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
                .alias("a1")
                .mrUrl("https://host/mr/1")
                .deployment(
                    new Deployment(
                        1,
                        List.of(Deployment.Target.of("proj", "dev").withMerge("abc1234")),
                        1,
                        null))
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.actions())
        .filteredOn(action -> action.id().equals("deploy"))
        .singleElement()
        .extracting(TaskView.ActionView::again)
        .isEqualTo(true);
  }

  @Test
  void offersTheRevertOnCardWhoseDeployBrokeOffAfterRepositoryLanded() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder(
                    List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                    TaskStatus.APPROVED)
                .alias("a1")
                .deployment(apiLandedWebWaiting)
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("api", "dev", "web", "dev"));

    assertThat(view.actions()).extracting(TaskView.ActionView::id).contains("revert");
  }

  @Test
  void marksMultiRepoTaskAsSoonAsOneRepositoryHasLanded() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder(
                    List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                    TaskStatus.DEPLOY_CONFLICT)
                .alias("a1")
                .deployment(apiLandedWebWaiting)
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("api", "dev", "web", "dev"));

    assertThat(view.deployed()).isTrue();
  }

  @Test
  void asksBeforeEveryPressThatCannotBeTakenBackNamingTheBranchEachRepositoryWrites() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder(
                    List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                    TaskStatus.APPROVED)
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("php", "main"));

    assertThat(view.confirmations()).containsOnlyKeys("deploy", "revert", "done");
    assertThat(view.confirmations().get("deploy"))
        .contains("php → main")
        .contains("java → no deployBranch in jagt.yml");
  }

  @Test
  void carriesTheRequestAgeAndTheApprovalOnlyWhileRequestIsOpen() {
    TaskState open =
        TaskState.builder("api", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://host/mr/1")
            .requestOpenedAt(1_700_000_000_000L)
            .approved(true)
            .build();
    TaskState none =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .requestOpenedAt(1_700_000_000_000L)
            .approved(true)
            .build();

    TaskView withRequest =
        TaskView.of(
            rules,
            "ABC-1",
            open,
            RoundState.of(open.standing().report(), false),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));
    TaskView withoutRequest =
        TaskView.of(
            rules,
            "ABC-1",
            none,
            RoundState.of(none.standing().report(), false),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(withRequest.requestOpenedAt()).isEqualTo(1_700_000_000_000L);
    assertThat(withRequest.approved()).isTrue();
    assertThat(withoutRequest.requestOpenedAt()).isZero();
    assertThat(withoutRequest.approved()).isNull();
  }

  @Test
  void readsTaskWithNoWatchAsOneNothingPolls() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("api", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://host/mr/1")
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            null,
            Map.of("api", "dev"));

    assertThat(view.owner()).isEqualTo(Owner.CI);
  }

  @Test
  void namesReviewedTaskAsAwaitingTheApprovalOfItsReviewers() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("api", "/wt", TaskStatus.REVIEWED).build(),
            RoundState.NONE,
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.statusLabel()).isEqualTo("awaiting approval");
  }

  @Test
  void namesTheMasterAsTheStatusLabelWhileItReadsTheRound() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("api", "/wt", TaskStatus.REVIEW_PENDING).build(),
            RoundState.NONE.withArbiterReading(true),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.statusLabel()).isEqualTo("master review");
  }

  @Test
  void abc42StoppedOnRoundsInActIsTheMastersToDecideBeforeItWasAsked() {
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds"))
            .build();
    Arbiter.Profile act = new Arbiter.Profile(MasterMode.ACT, java.util.Set.of());

    TaskView view =
        TaskView.of(
            rules,
            "ABC-42",
            stopped,
            RoundState.NONE.withArbiter(act),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.owner()).isEqualTo(Owner.AGENT);
    assertThat(view.attention()).isEqualTo(Attention.NONE);
    assertThat(view.statusLabel()).isEqualTo("master review");
    assertThat(view.actions()).extracting(TaskView.ActionView::id).contains("lift");
  }

  @Test
  void abc42StoppedOnRoundsInActIsTheHumansToLiftOnceTheMasterWasAsked() {
    TaskStop stop = new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds");
    TaskState asked =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .stop(stop)
            .master(Master.defaults().withAskedStop(stop.key()))
            .build();
    Arbiter.Profile act = new Arbiter.Profile(MasterMode.ACT, java.util.Set.of());

    TaskView view =
        TaskView.of(
            rules,
            "ABC-42",
            asked,
            RoundState.NONE.withArbiter(act),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.owner()).isEqualTo(Owner.YOU);
    assertThat(view.ask()).isEqualTo("lift the stop — same round sent too often");
  }

  @Test
  void carriesTheStopsReasonEvidenceAdviceAndTimeWhereJagtStoppedTheTask() {
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.CI_POLLING)
            .stop(
                new TaskStop(
                    TaskStop.Trigger.SHIPS,
                    1_700_000_000_000L,
                    "a ship asked past 3 without a new note"))
            .build();

    TaskView view =
        TaskView.of(
            rules,
            "ABC-42",
            stopped,
            RoundState.of(stopped.standing().report(), false),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.ask()).isEqualTo("lift the stop — ships with no new note");
    assertThat(view.stop())
        .isEqualTo(
            new TaskView.Stop(
                TaskStop.Trigger.Kind.GUARD,
                "ships with no new note",
                "a ship asked past 3 without a new note",
                "Check a new note or commit waits to ship; if one does, report a jagt defect",
                1_700_000_000_000L,
                ""));
  }

  @Test
  void tellsYourPauseApartFromJagtStop() {
    TaskState paused =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.PAUSE, 1_700_000_000_000L, "you paused it"))
            .build();

    TaskView view =
        TaskView.of(
            rules, "ABC-42", paused, RoundState.NONE, AutoReviewWatch.none(), Map.of("api", "dev"));

    assertThat(view.attention()).isEqualTo(Attention.OPTIONAL);
    assertThat(view.ask()).isEqualTo("you can lift the pause");
    assertThat(view.asking().hint()).contains("you paused this task", "lift your pause");
    assertThat(view.statusLabel()).isEqualTo("‖ paused");
    assertThat(view.stop()).extracting(TaskView.Stop::kind).isEqualTo(TaskStop.Trigger.Kind.PAUSE);
    assertThat(view.history().getLast().label()).isEqualTo("‖ paused");
  }

  @Test
  void carriesNoStopWhereJagtStoppedNothing() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-42",
            TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.stop()).isNull();
  }

  @Test
  void showsRedWhoseRetryIsArmedAsRunningWithWhatItWaitsOn() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://host/mr/1")
                .pipelineStatus("failed")
                .infraRetries(
                    List.of(
                        InfraRetry.armed(
                            36_000_000,
                            "shop",
                            "https://host.invalid/p/1",
                            "unit-tests",
                            "runner_system_failure",
                            1,
                            3)))
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.pipeline()).isEqualTo(Pipeline.RUNNING);
    assertThat(view.pipelineSaid())
        .isEqualTo("runner_system_failure: unit-tests · retry 1/3 at 10:00 UTC");
  }

  @Test
  void showsRedWithNoRetryArmedAsRed() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
                .mrUrl("http://host/mr/1")
                .pipelineStatus("failed")
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.pipeline()).isEqualTo(Pipeline.RED);
    assertThat(view.pipelineSaid()).isEqualTo("failed");
  }

  @Test
  void namesWhatTheSessionWaitsOnAndWhenJagtNextSaysSo() {
    TaskState waiting =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .message("db down: ping db.internal")
            .report(AgentReport.WAIT)
            .build();
    waiting =
        waiting.withTurn(
            waiting
                .work()
                .turnOrNone()
                .withMark(Wake.MARK, new Wake(0, 36_000_000, Step.WAIT_OVER).mark()));

    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            waiting,
            RoundState.of(waiting.standing().report(), false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.waiting()).isEqualTo("db down: ping db.internal\nnext try 10:00 UTC");
  }

  @Test
  void saysNothingOfWaitForSessionThatDoesNotWait() {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("proj", "dev"));

    assertThat(view.waiting()).isNull();
  }

  @Test
  void namesReviewedTaskMergedWhenEveryRequestOfItIs() {
    TaskState merged =
        TaskState.builder("api", "/wt", TaskStatus.REVIEWED)
            .mrUrl("https://host/x/-/merge_requests/1")
            .build()
            .withMerged("api", true);

    TaskView view =
        TaskView.of(
            rules, "ABC-42", merged, RoundState.NONE, AutoReviewWatch.none(), Map.of("api", "dev"));

    assertThat(view.statusLabel()).isEqualTo("merged");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void approvePlanHoverNamesWhichPlanWaits(boolean laterPlan) {
    TaskView view =
        TaskView.of(
            rules,
            "ABC-1",
            TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING)
                .planApproved(laterPlan)
                .build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of("api", "dev"));

    assertThat(view.actions())
        .filteredOn(action -> action.id().equals("approve-plan"))
        .extracting(TaskView.ActionView::hint)
        .singleElement()
        .asString()
        .startsWith(laterPlan ? "a later plan waits" : "the first plan waits");
  }
}
