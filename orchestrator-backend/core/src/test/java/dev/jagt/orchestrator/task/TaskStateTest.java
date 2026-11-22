package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.plan.Probe;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.task.usage.AgentSpend;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TaskStateTest {

  @Test
  void startsItsHistoryAtTheStatusItWasCreatedWith() {
    TaskState created =
        TaskState.builder("proj", "/wt", TaskStatus.NEW).lastActiveTimestamp(1_000).build();

    assertThat(created.standing().history())
        .containsExactly(new StatusChange(TaskStatus.NEW, 1_000, null));
  }

  @Test
  void recordsEveryMoveInTheOrderItHappened() {
    TaskState shipped =
        TaskState.builder("proj", "/wt", TaskStatus.NEW)
            .lastActiveTimestamp(1_000)
            .build()
            .withStatus(TaskStatus.IN_PROGRESS, "working", 2_000)
            .withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000)
            .withStatus(TaskStatus.SHIPPING, "shipping", 2_000);

    assertThat(shipped.standing().history())
        .extracting(StatusChange::status)
        .containsExactly(
            TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING, TaskStatus.SHIPPING);
  }

  @Test
  void recordsNothingForKeepAliveBecauseTheStatusDidNotChange() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.NEW)
            .lastActiveTimestamp(1_000)
            .build()
            .withStatus(TaskStatus.IN_PROGRESS, "working", 2_000);

    TaskState stillWorking = working.touched(2_000).touched(3_000).touched(4_000);

    assertThat(stillWorking.standing().history())
        .extracting(StatusChange::status)
        .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS);
  }

  @Test
  void dropsTheOldestMovesAfterTheOpeningOneOnceFiftyAreRecordedSoStateJsonStaysSmall() {
    List<StatusChange> fifty =
        IntStream.range(0, 50)
            .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null))
            .toList();
    TaskState full =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fifty).build();

    TaskState moved = full.withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000);

    assertThat(moved.standing().history()).hasSize(50);
    assertThat(moved.standing().history().getFirst().at()).isZero();
    assertThat(moved.standing().history().get(1).at()).isEqualTo(2);
    assertThat(moved.standing().history().getLast().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @Test
  void reportsTimeInTheCurrentStatusRatherThanTimeSinceTheLastKeepAlive() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000, null)))
            .build();

    TaskState keptAlive = working.touched(2_000);

    assertThat(keptAlive.standing().statusSince()).isEqualTo(1_000);
    assertThat(keptAlive.standing().lastActiveTimestamp()).isEqualTo(2_000);
  }

  @Test
  void reportsTheActivityStampForTaskWrittenBeforeHistoryExisted() {
    TaskState legacy =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .lastActiveTimestamp(5_000)
            .alias("a1")
            .history(List.of())
            .build();

    assertThat(legacy.standing().history()).isEmpty();
    assertThat(legacy.standing().statusSince()).isEqualTo(5_000);
  }

  @Test
  void opensNewPollingWindowForEveryRoundShippedOntoTheSameRequest() {
    TaskState polling =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("https://host/mr/9")
            .mrCreatedAt(1_000L)
            .lastPolledAt(9_000L)
            .build();

    TaskState round = polling.withReviewRound("https://host/mr/9", 2_000L);

    assertThat(round.round().mrCreatedAt()).isEqualTo(2_000L);
    assertThat(round.round().lastPolledAt()).isZero();
    assertThat(round.primary().mrUrl()).isEqualTo("https://host/mr/9");
  }

  @Test
  void stampsTheShippedTreeOnTheShipJustTaken() {
    TaskState shipping =
        TaskState.builder("proj", "/wt", TaskStatus.SHIPPING)
            .history(List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, null)))
            .build();

    TaskState stamped = shipping.withLastShipTree("t1");

    assertThat(stamped.standing().history().getLast().tree()).isEqualTo("t1");
  }

  @Test
  void stampsNoTreeOnStepThatIsNotShip() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000, null)))
            .build();

    TaskState stamped = working.withLastShipTree("t1");

    assertThat(stamped.standing().history().getLast().tree()).isNull();
  }

  @Test
  void recordsSomethingDoneToTheTaskEvenWhenItsStatusDoesNotChange() {
    TaskState polling =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .history(List.of(new StatusChange(TaskStatus.CI_POLLING, 1_000, null)))
            .build();

    TaskState again =
        polling.withStatus(TaskStatus.CI_POLLING, "review request: https://host/mr/9", true, 2_000);

    assertThat(again.standing().history()).hasSize(2);
    assertThat(again.standing().statusSince()).isEqualTo(again.standing().history().getLast().at());
  }

  @Test
  void dropsRepeatedStatusTaskOnlyKeepsSayingAboutItself() {
    TaskState polling =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .history(List.of(new StatusChange(TaskStatus.CI_POLLING, 1_000, null)))
            .build();

    TaskState again = polling.withStatus(TaskStatus.CI_POLLING, "still polling", 2_000);

    assertThat(again.standing().history()).hasSize(1);
  }

  @Test
  void keepsTheStatusStampOfLegacyTaskWhenTheAgentOnlyPingsItsKeepAlive() {
    TaskState legacy =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(1_700_000_000_000L)
            .message("working")
            .alias("a1")
            .history(List.of())
            .build();

    TaskState afterKeepAlive = legacy.touched(1_700_000_001_000L);

    assertThat(afterKeepAlive.standing().statusSince()).isEqualTo(1_700_000_000_000L);
    assertThat(afterKeepAlive.standing().lastActiveTimestamp()).isEqualTo(1_700_000_001_000L);
  }

  @Test
  void keepsWhatTheHostSaidAboutTheChecksWhenTheTaskGoesOnMoving() {
    TaskState red =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withChecksRead("failed", null);

    TaskState reported = red.withStatus(TaskStatus.CI_FAILED, "build broken", 2_000);

    assertThat(reported.checks().pipelineStatus()).isEqualTo("failed");
  }

  @Test
  void readsStateFileWrittenBeforeTaskCouldSpanRepositories() {
    String legacy =
        """
        {
          "project": "demo",
          "worktreePath": "/wt/ABC-1-demo",
          "status": "CI_POLLING",
          "lastActiveTimestamp": 1700000000000,
          "message": "MR: https://host/mr/9",
          "alias": "a1",
          "remoteUrl": "git@host:g/p.git",
          "title": "Widget layout is off",
          "mrUrl": "https://host/mr/9",
          "deployCommit": "cafebabe1234",
          "mrCreatedAt": 1700000000000,
          "lastPolledAt": 0
        }\
        """;

    TaskState task = new JsonMapper().readValue(legacy, TaskState.class);

    assertThat(task.repos()).hasSize(1);
    assertThat(task.project()).isEqualTo("demo");
    assertThat(task.worktreePath()).isEqualTo("/wt/ABC-1-demo");
    assertThat(task.remoteUrl()).isEqualTo("git@host:g/p.git");
    assertThat(task.mrUrl()).isEqualTo("https://host/mr/9");
    assertThat(
            Objects.requireNonNull(task.work().deployment()).targets().getFirst().landing().merge())
        .isEqualTo("cafebabe1234");
    assertThat(task.standing().status()).isEqualTo(TaskStatus.CI_POLLING);
    assertThat(task.ticket().alias()).isEqualTo("a1");
  }

  @Test
  void writesRepositoriesAsListAndReadsThemBack() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/ABC-1-php"), TaskRepo.of("java", "/wt/ABC-1-java")),
                TaskStatus.IN_PROGRESS)
            .alias("a1")
            .title("contract change")
            .build();
    JsonMapper mapper = new JsonMapper();

    String json = mapper.writeValueAsString(task);
    TaskState reread = mapper.readValue(json, TaskState.class);

    assertThat(json)
        .contains("\"repos\"")
        .doesNotContain("\"worktreePath\":\"/wt/ABC-1-php\",\"project\"");
    assertThat(reread.projects()).containsExactly("php", "java");
    assertThat(reread.worktreePath()).isEqualTo("/wt/ABC-1-php");
  }

  @Test
  void keepsReviewRequestOnTheRepositoryItBelongsTo() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.REVIEW_PENDING)
            .build();

    TaskState shipped =
        task.withReviewRound("php", "https://host/php/mr/1", 2_000)
            .withReviewRound("java", "https://host/java/mr/2", 2_000);

    assertThat(shipped.repo("php").orElseThrow().mrUrl()).isEqualTo("https://host/php/mr/1");
    assertThat(shipped.repo("java").orElseThrow().mrUrl()).isEqualTo("https://host/java/mr/2");
    assertThat(shipped.hasReviewRequest()).isTrue();
  }

  @Test
  void keepsKnownRequestAgeWhenReadCannotSayWhenItWasOpened() {
    TaskState stamped =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .requestOpenedAt(1_700_000_000_000L)
            .build();

    assertThat(stamped.withRequestOpenedAt(0).round().requestOpenedAt())
        .isEqualTo(1_700_000_000_000L);
  }

  @Test
  void datesRequestItJustOpenedByTheClockInsteadOfLeavingTheAgeBlank() {
    TaskState stamped =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .requestOpenedAt(1_700_000_000_000L)
            .build();

    long opened = stamped.withMrUrl("http://mr/2", 1_700_000_005_000L).round().requestOpenedAt();

    assertThat(opened).isEqualTo(1_700_000_005_000L);
    assertThat(stamped.withMrUrl("http://mr/1", 1_700_000_005_000L).round().requestOpenedAt())
        .isEqualTo(1_700_000_000_000L);
  }

  @Test
  void keepsWhatWasReadWhenTheLinkHasNotChanged() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .mrUrl("https://host/mr/1")
            .approved(true)
            .pipelineStatus("success")
            .build();

    assertThat(task.withMrUrl("https://host/mr/1", 2_000).round().approved()).isTrue();
  }

  @Test
  void reportsItsHistoryAsCappedOnlyOnceFiftyStepsAreHeld() {
    List<StatusChange> fortyNine =
        IntStream.range(0, 49)
            .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null))
            .toList();
    List<StatusChange> fifty =
        IntStream.range(0, 50)
            .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null))
            .toList();

    assertThat(
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .history(fortyNine)
                .build()
                .standing()
                .historyAtCap())
        .isFalse();
    assertThat(
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .history(fifty)
                .build()
                .standing()
                .historyAtCap())
        .isTrue();
  }

  @Test
  void keepsAllFiftyMovesWhenTheFiftiethIsRecorded() {
    List<StatusChange> fortyNine =
        IntStream.range(0, 49)
            .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null))
            .toList();
    TaskState almostFull =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fortyNine).build();

    TaskState moved = almostFull.withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000);

    assertThat(moved.standing().history()).hasSize(50);
    assertThat(moved.standing().history().getFirst().at()).isZero();
  }

  @Test
  void keepsTheOpeningStepWhenTheOldestStepsAreTrimmed() {
    List<StatusChange> fifty =
        new java.util.ArrayList<>(List.of(new StatusChange(TaskStatus.NEW, 0, ActionOrigin.BOARD)));
    IntStream.range(1, 50)
        .forEach(i -> fifty.add(new StatusChange(TaskStatus.IN_PROGRESS, i, null)));
    TaskState full =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fifty).build();

    TaskState moved = full.withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000);

    assertThat(moved.standing().history().getFirst())
        .isEqualTo(new StatusChange(TaskStatus.NEW, 0, ActionOrigin.BOARD));
  }

  @Test
  void countsOneRoundForEveryEntryIntoCiPolling() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .history(
                List.of(
                    new StatusChange(TaskStatus.NEW, 1, null),
                    new StatusChange(TaskStatus.CI_POLLING, 2, null),
                    new StatusChange(TaskStatus.IN_PROGRESS, 3, null),
                    new StatusChange(TaskStatus.REVIEW_PENDING, 4, null),
                    new StatusChange(TaskStatus.CI_POLLING, 5, null)))
            .build();

    assertThat(task.standing().rounds()).isEqualTo(2);
  }

  @Test
  void stampsWhoCausedTheLastStepAndNoOtherStep() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .history(
                List.of(
                    new StatusChange(TaskStatus.NEW, 1, ActionOrigin.BOARD),
                    new StatusChange(TaskStatus.REVIEW_PENDING, 2, null)))
            .build();

    TaskState stamped = task.withLastChangeOrigin(ActionOrigin.TRACKER);

    assertThat(stamped.standing().history())
        .extracting(StatusChange::origin)
        .containsExactly(ActionOrigin.BOARD, ActionOrigin.TRACKER);
  }

  @Test
  void stampsNobodyOnTaskThatHasNoStepYet() {
    TaskState task =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"NEW","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(task.withLastChangeOrigin(ActionOrigin.BOARD)).isSameAs(task);
  }

  @Test
  void keepsStopWrittenInsideTheLoopBeforeTheStopStoodOnTheTask() {
    TaskState task =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0,"loop":{"rounds":[{"at":1,"threads":4,"failing":"","digest":"a"}],"since":1,
                         "stop":{"trigger":"ROUNDS","at":2,"evidence":"verify sent 3 rounds running in 24h"}}}\
                """,
                TaskState.class);

    assertThat(task.work().stop())
        .isEqualTo(new TaskStop(TaskStop.Trigger.ROUNDS, 2, "verify sent 3 rounds running in 24h"));
    assertThat(task.round().trailOrNone().rounds()).hasSize(1);
  }

  @Test
  void countsEveryRefusalPerKindAcrossRefusals() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

    TaskState refused =
        task.refused(RefusalKind.STATUS_REPORT)
            .refused(RefusalKind.STATUS_REPORT)
            .refused(RefusalKind.GIT_STATE);

    assertThat(refused.gate().refusals())
        .containsEntry(RefusalKind.STATUS_REPORT, 2)
        .containsEntry(RefusalKind.GIT_STATE, 1);
  }

  @Test
  void takesTheProjectDefaultBaseBranchOnlyWhereTheTaskNamedNone() {
    TaskState own = TaskState.builder("proj", "/wt", TaskStatus.NEW).baseBranch("release").build();
    TaskState blank = TaskState.builder("proj", "/wt", TaskStatus.NEW).baseBranch(" ").build();
    TaskState none = TaskState.builder("proj", "/wt", TaskStatus.NEW).build();

    assertThat(own.ticket().baseBranchOr("main")).isEqualTo("release");
    assertThat(blank.ticket().baseBranchOr("main")).isEqualTo("main");
    assertThat(none.ticket().baseBranchOr("main")).isEqualTo("main");
  }

  @Test
  void followsTheConfigDefaultForAutoReviewOnlyWhereTheTaskSetNone() {
    TaskState on = TaskState.builder("proj", "/wt", TaskStatus.NEW).autoReview(true).build();
    TaskState off = TaskState.builder("proj", "/wt", TaskStatus.NEW).autoReview(false).build();
    TaskState unset = TaskState.builder("proj", "/wt", TaskStatus.NEW).build();

    assertThat(on.round().autoReviewEnabled(false)).isTrue();
    assertThat(off.round().autoReviewEnabled(true)).isFalse();
    assertThat(unset.round().autoReviewEnabled(true)).isTrue();
    assertThat(unset.round().autoReviewEnabled(false)).isFalse();
  }

  @Test
  void findsTheReviewRequestOfOneProjectAndNoneForProjectWithoutOne() {
    TaskState task =
        TaskState.builder(
                List.of(
                    TaskRepo.of("php", "/wt/php").withMrUrl("https://host/php/mr/1"),
                    TaskRepo.of("java", "/wt/java"),
                    TaskRepo.of("web", "/wt/web").withMrUrl(" ")),
                TaskStatus.REVIEW_PENDING)
            .build();

    assertThat(task.reviewRequestOf("php")).contains("https://host/php/mr/1");
    assertThat(task.reviewRequestOf("java")).isEmpty();
    assertThat(task.reviewRequestOf("web")).isEmpty();
  }

  @Test
  void recordsOneRoundForShipAcrossRepositoriesCarryingTheSessionRepositoryLink() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.REVIEW_PENDING)
            .build();

    TaskState shipped =
        task.withReviewRound(
            Map.of("php", "https://host/php/mr/1", "java", "https://host/java/mr/2"), 2_000);

    assertThat(shipped.repo("php").orElseThrow().mrUrl()).isEqualTo("https://host/php/mr/1");
    assertThat(shipped.repo("java").orElseThrow().mrUrl()).isEqualTo("https://host/java/mr/2");
    assertThat(shipped.mrUrl()).isEqualTo("https://host/php/mr/1");
  }

  @Test
  void leavesTheSessionRepositoryUnlinkedWhenTheShipLandedOnlyInAnother() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.REVIEW_PENDING)
            .build();

    TaskState shipped = task.withReviewRound(Map.of("java", "https://host/java/mr/2"), 2_000);

    assertThat(shipped.repo("php").orElseThrow().mrUrl()).isNull();
    assertThat(shipped.repo("java").orElseThrow().mrUrl()).isEqualTo("https://host/java/mr/2");
    assertThat(shipped.mrUrl()).isEqualTo("https://host/java/mr/2");
    assertThat(shipped.round().mrCreatedAt()).isEqualTo(2_000);
  }

  @Test
  void leavesTheTaskAloneWhenShipLandedInNoRepository() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    assertThat(task.withReviewRound(Map.of(), 2_000)).isSameAs(task);
  }

  @Test
  void addsMeteredUsageToWhatWasMeteredBefore() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withUsageAdded(TokenUsage.ofCall(10, 0, 5, 0))
            .withUsageAdded(TokenUsage.ofCall(20, 0, 5, 0));

    assertThat(task.gate().usageOrNone().calls()).isEqualTo(2);
    assertThat(task.gate().usageOrNone().inputTokens()).isEqualTo(30);
  }

  @Test
  void keepsTheLastPollAndTheRemoteUrlItWasGiven() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.DEPLOYED)
            .build();

    TaskState noted = task.withLastPolledAt(42).withRemoteUrl("java", "git@host:g/java.git");

    assertThat(noted.round().lastPolledAt()).isEqualTo(42);
    assertThat(noted.repo("java").orElseThrow().remoteUrl()).isEqualTo("git@host:g/java.git");
    assertThat(noted.repo("php").orElseThrow().remoteUrl()).isNull();
  }

  @Test
  void listsOnlyTheRepositoriesThatCarryProjectName() {
    TaskState task =
        TaskState.builder(
                List.of(
                    TaskRepo.of("php", "/wt/php"),
                    TaskRepo.of(null, "/wt/x"),
                    TaskRepo.of(" ", "/wt/y")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThat(task.projects()).containsExactly("php");
  }

  @Test
  void seedsLegacyTaskWithoutAnActivityStampAtTheMomentItWasFirstMoved() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":0,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    TaskState moved = legacy.withStatus(TaskStatus.IN_PROGRESS, "ping", true, 1_700_000_005_000L);

    assertThat(moved.standing().history().getFirst().at()).isEqualTo(1_700_000_005_000L);
  }

  @Test
  void readsBackEveryFieldItWroteIncludingWhatAnEarlierFileCouldOmit() {
    JsonMapper mapper = new JsonMapper();
    TaskState written =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withChecksUnread(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"))
            .withProbes(List.of(Probe.specified(Probe.Kind.COMMAND, List.of("make"), 0)))
            .refused(RefusalKind.GIT_STATE);

    TaskState read = mapper.readValue(mapper.writeValueAsString(written), TaskState.class);

    assertThat(read.checks().checksUnread())
        .containsExactly(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"));
    assertThat(read.gate().probes()).hasSize(1);
    assertThat(read.gate().refusals()).containsEntry(RefusalKind.GIT_STATE, 1);
    assertThat(read.standing().history()).hasSize(1);
  }

  @Test
  void recordsWhatJagtFoundAboutTaskWithoutCountingItAsTheAgentMoving() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).lastActiveTimestamp(1_000).build();
    AgentSpend spend = AgentSpend.NONE.plus(TokenUsage.ofCall(10, 0, 5, 0), "log", 7);
    GitTrust trust = GitTrust.of(Map.of("main", "abc"));
    List<Probe> probes = List.of(Probe.specified(Probe.Kind.REF, List.of("main"), 0));

    TaskState found =
        task.withAgentSpend(spend)
            .withGitTrust(trust)
            .withProbes(probes)
            .withApproved(true)
            .withChecksUnread(new ChecksUnread(1_000, ""));

    assertThat(found.gate().agentSpend()).isEqualTo(spend);
    assertThat(found.gate().gitTrust()).isEqualTo(trust);
    assertThat(found.gate().probes()).isEqualTo(probes);
    assertThat(found.round().approved()).isTrue();
    assertThat(found.checks().unread()).isTrue();
    assertThat(found.standing().lastActiveTimestamp()).isEqualTo(1_000);
  }

  @Test
  void readsFileWrittenBeforeThePlanGateExistedAsNothingToWaitFor() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"NEW","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(legacy.gate().planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void keepsTheChecksFlaggedUnreadInFileWrittenBeforeTheReadsWereKept() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"CI_POLLING","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0,"pipelineStatus":"failed","pipelineUnread":true}\
                """,
                TaskState.class);

    assertThat(legacy.checks().unread()).isTrue();
  }

  @Test
  void keepsOnlyTheReadsRunningThatCanStopTheTask() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withChecksUnread(new ChecksUnread(1_000, "a"))
            .withChecksUnread(new ChecksUnread(2_000, "b"))
            .withChecksUnread(new ChecksUnread(3_000, "c"))
            .withChecksUnread(new ChecksUnread(4_000, "d"));

    assertThat(task.checks().checksUnread())
        .containsExactly(
            new ChecksUnread(2_000, "b"),
            new ChecksUnread(3_000, "c"),
            new ChecksUnread(4_000, "d"));
  }

  @Test
  void forgetsTheReadsRunningThatCouldNotListTheChecksOnceOneDid() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withChecksUnread(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"));

    TaskState read = task.withChecksRead("success", null);

    assertThat(read.checks().checksUnread()).isEmpty();
  }

  @Test
  void addsTheRepositorySessionAskedForAfterItsOwnAndEndsTheRequest() {
    TaskState asked =
        TaskState.builder("api", "/wt-api", TaskStatus.IN_PROGRESS).repoRequest("registry").build();

    TaskState added = asked.withRepo(TaskRepo.of("registry", "/wt-registry"));

    assertThat(added.projects()).containsExactly("api", "registry");
    assertThat(added.work().repoRequest()).isNull();
  }

  @Test
  void keepsThePrimaryRepositoryWhenTheTaskHoldsOthers() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThat(task.withoutRepo("api").projects()).containsExactly("api", "web");
  }

  @Test
  void asksForNoRepositoryUntilTheSessionNamesOne() {
    TaskState working = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

    assertThat(working.work().asksForRepository()).isFalse();
  }

  @Test
  void reportsTheChecksUnreadOnlyWhileReadRunningCouldNotListThem() {
    TaskState clear = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState unread =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .checksUnread(List.of(new ChecksUnread(1, "403")))
            .build();

    assertThat(clear.checks().unread()).isFalse();
    assertThat(unread.checks().unread()).isTrue();
  }

  @Test
  void holdsNoTurnUntilOneIsRecordedAndThenTheOneRecorded() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    Turn relayed = Turn.none().relayed("round", "d1", "", 100);

    assertThat(task.work().turnOrNone()).isEqualTo(Turn.none());
    assertThat(task.withTurn(relayed).work().turnOrNone()).isEqualTo(relayed);
  }

  @Test
  void keepsTheStopAndTheTrailItWasGivenAndClearsTheStopOnLift() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskStop stop = new TaskStop(TaskStop.Trigger.ROUNDS, 2, "verify sent 3 rounds running");
    RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 4, "", "a", ""));

    TaskState stopped = task.withStop(stop).withTrail(trail);
    TaskState lifted = stopped.lifted(9);

    assertThat(stopped.work().stopped()).isTrue();
    assertThat(stopped.work().stop()).isEqualTo(stop);
    assertThat(stopped.round().trailOrNone()).isEqualTo(trail);
    assertThat(lifted.work().stopped()).isFalse();
    assertThat(lifted.round().trailOrNone()).isEqualTo(RoundTrail.liftedAt(9));
  }

  @Test
  void keepsTheBaseBranchAndThePlanGateItWasGiven() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

    TaskState given = task.withBaseBranch("release").withPlanGate(PlanGate.REQUIRED);

    assertThat(given.ticket().baseBranchOr("main")).isEqualTo("release");
    assertThat(given.gate().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void stampsTheReasonOnTheLastStepAndNoOtherStep() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .history(
                List.of(
                    new StatusChange(TaskStatus.NEW, 1, ActionOrigin.BOARD),
                    new StatusChange(TaskStatus.REVIEW_PENDING, 2, null)))
            .build();

    TaskState stamped = task.withLastChangeWhy("work done");

    assertThat(stamped.standing().history())
        .extracting(StatusChange::why)
        .containsExactly(null, "work done");
  }

  @Test
  void recordsRequestWithoutCountingItAsAgentActivity() {
    TaskState working =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(7_000).build();

    TaskState asked = working.withRepoRequest("registry");

    assertThat(asked.work().asksForRepository()).isTrue();
    assertThat(asked.standing().lastActiveTimestamp()).isEqualTo(7_000);
  }

  @Test
  void keepsTheSessionsSwitchJudgementsThroughStateJsonAndLetsTheLastWordStand() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .judged(Map.of("visualize", "no — config-only change", "autoplan", "no — one file"))
            .judged(Map.of("visualize", "yes — a screen changed"));
    JsonMapper mapper = new JsonMapper();

    TaskState reread = mapper.readValue(mapper.writeValueAsString(task), TaskState.class);

    assertThat(reread.gate().switches())
        .containsOnly(
            Map.entry("visualize", "yes — a screen changed"),
            Map.entry("autoplan", "no — one file"));
  }

  @Test
  void keepsStopWhereHumansDeployLands() {
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .stop(new TaskStop(TaskStop.Trigger.MISS, 5, "no case"))
            .build();

    TaskState deployed = stopped.withStatus(TaskStatus.DEPLOYED, "deployed", true, 2_000);

    assertThat(deployed.work().stopped()).isTrue();
  }

  @Test
  void readsChecksAndApprovalAsUnknownWhenTheHeadMovedOffTheCommitTheyWereReadOn() {
    TaskState read =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withHead("aaa")
            .withChecksRead("failed", "aaa")
            .withApproved(true);

    TaskState pushed = read.withHead("bbb");

    assertThat(pushed.checks().read()).isNull();
    assertThat(pushed.approvedRead()).isNull();
  }

  @Test
  void keepsChecksWhenTheHeadIsStillTheCommitTheyWereReadOn() {
    TaskState read =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withHead("aaa")
            .withChecksRead("failed", "aaa")
            .withApproved(true);

    assertThat(read.withHead("aaa").checks().read()).isEqualTo("failed");
    assertThat(read.withHead("aaa").approvedRead()).isTrue();
  }

  @Test
  void readsChecksAsUnknownWhenStoredReadNamesNoCommitOnceHeadIsKnown() {
    TaskState legacy =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("failed").build();

    assertThat(legacy.withHead("aaa").checks().read()).isNull();
  }

  @Test
  void readsAnOldConflictWatchAsTheConflictOfTheDeploymentItsTaskStandsIn() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"DEPLOY_CONFLICT","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0,"conflict":{"conflictSince":4,"worktree":"/wt-deploy",
                 "progress":{"unmerged":7,"staged":0,"digest":"d"},"movedAt":6,"told":{"at":8,"reason":"stuck"}}}\
                """,
                TaskState.class);

    assertThat(legacy.currentConflict())
        .hasValueSatisfying(conflict -> {
          assertThat(conflict.progress()).isEqualTo(new Conflict.Progress(7, 0, "d"));
          assertThat(conflict.told()).isEqualTo(new Conflict.Told(8, "stuck"));
          assertThat(conflict.movedAt()).isEqualTo(6);
        });
  }

  @Test
  void readsTaskInDeployConflictThatNothingRecordedAsOneNotYetHanded() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"DEPLOY_CONFLICT","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(legacy.currentConflict())
        .hasValueSatisfying(conflict -> assertThat(conflict.handed()).isFalse());
  }

  @Test
  void readsTheMergeAndFixesOfRepositoryIntoTheTargetOfItsProject() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"repos":[{"project":"php","worktreePath":"/wt/php","deployCommit":"cafe1","deployFixes":["f1x1","f1x2"]},
                          {"project":"java","worktreePath":"/wt/java","deployCommit":"beef2"}],
                 "status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(Objects.requireNonNull(old.work().deployment()).targets())
        .extracting(
            Deployment.Target::project,
            target -> target.landing().merge(),
            target -> target.landing().fixes())
        .containsExactly(
            tuple("php", "cafe1", List.of("f1x1", "f1x2")), tuple("java", "beef2", List.of()));
    assertThat(old.work().deployment().live()).isTrue();
  }

  @Test
  void placesAnOldConflictOnTheTargetWhoseDeployWorktreeItStoodIn() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"repos":[{"project":"php","worktreePath":"/one/ABC-1-php"},
                          {"project":"java","worktreePath":"/two/ABC-1-java"}],
                 "status":"DEPLOY_CONFLICT","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
                 "conflict":{"conflictSince":3,"worktree":"/two/ABC-1-deploy",
                             "progress":{"unmerged":1,"staged":0,"digest":"d"},"movedAt":4}}\
                """,
                TaskState.class);

    assertThat(Objects.requireNonNull(old.work().deployment()).conflictedTarget())
        .hasValueSatisfying(target -> {
          assertThat(target.project()).isEqualTo("java");
          assertThat(Objects.requireNonNull(target.progress().conflict()).since()).isEqualTo(3);
        });
  }

  @Test
  void placesAnOldConflictOnThePrimaryProjectWhenNoDeployWorktreeMatches() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"repos":[{"project":"php","worktreePath":"/one/ABC-1-php"},
                          {"project":"java","worktreePath":"/two/ABC-1-java"}],
                 "status":"DEPLOY_CONFLICT","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
                 "conflict":{"conflictSince":3,"worktree":"/else/ABC-1-deploy","movedAt":4}}\
                """,
                TaskState.class);

    assertThat(
            Objects.requireNonNull(old.work().deployment())
                .conflictedTarget()
                .orElseThrow()
                .project())
        .isEqualTo("php");
  }

  @Test
  void keepsTheConflictOfDeployTargetAcrossSave() {
    TaskState conflicted =
        TaskState.builder("demo", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("demo", "dev")
                            .withConflict(
                                Conflict.of(Deployment.Cause.RED_PIPELINE, 2)
                                    .seen(new Conflict.Progress(1, 2, "d"), 3)
                                    .toldAt(4, "stuck")
                                    .heldBy(5))),
                    0,
                    null));
    JsonMapper mapper = new JsonMapper();

    TaskState reread = mapper.readValue(mapper.writeValueAsString(conflicted), TaskState.class);

    assertThat(reread.work().deployment()).isEqualTo(conflicted.work().deployment());
  }

  @Test
  void keepsTheSessionSignsAcrossSave() {
    TaskState signed =
        TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withSession(
                new Session(
                    5,
                    4,
                    3,
                    new Session.Halt(Session.Halt.Kind.FAILED, 6, "overloaded"),
                    2,
                    "/logs/s.jsonl",
                    9,
                    new Session.Start(
                        3, new Session.Window(Session.Window.State.RUNNING, 7, 8), null)));
    JsonMapper mapper = new JsonMapper();

    TaskState reread = mapper.readValue(mapper.writeValueAsString(signed), TaskState.class);

    assertThat(reread.work().session()).isEqualTo(signed.work().session());
  }

  @Test
  void readsTaskWrittenBeforeTheSessionWasRecordedAsOneWithNoSign() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(old.work().session()).isEqualTo(Session.defaults());
  }

  @Test
  void readsSilenceAnOlderFileStampedOnTheTaskAsTheSessionsQuiet() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0,"silentSince":4000,"silentBecause":"its turn failed"}\
                """,
                TaskState.class);

    assertThat(old.work().session().quietSince()).isEqualTo(4_000);
  }

  @Test
  void endsStoppedSessionWhenTheTaskMovesOnToStatusOfItsOwn() {
    TaskState stopped =
        TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withSession(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.GONE, 4_000, "boom"))
                    .withQuietSince(3_000));

    TaskState moved = stopped.withStatus(TaskStatus.REVIEW_PENDING, "shipped", 5_000);

    assertThat(moved.work().session().halt()).isNull();
    assertThat(moved.work().session().quietSince()).isZero();
  }

  @Test
  void keepsAnIdleHaltWhenTheTaskMovesOnToStatusOfItsOwn() {
    TaskState idle =
        TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withSession(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 4_000, null))
                    .withQuietSince(3_000));

    TaskState moved = idle.withStatus(TaskStatus.REVIEW_PENDING, "shipped", 5_000);

    assertThat(moved.work().session().halt()).isEqualTo(idle.work().session().halt());
    assertThat(moved.work().session().quietSince()).isZero();
  }

  @Test
  void holdsNoRepositoryUntilProjectIsRegistered() {
    assertThat(
            TaskState.builder(List.of(TaskRepo.of(null, "/scratch/ABC-1")), TaskStatus.NEW)
                .build()
                .unplaced())
        .isTrue();
    assertThat(TaskState.builder("api", "/wt", TaskStatus.NEW).build().unplaced()).isFalse();
  }
}
