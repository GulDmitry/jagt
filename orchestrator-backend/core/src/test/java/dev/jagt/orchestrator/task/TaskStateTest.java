package dev.jagt.orchestrator.task;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStateTest {

    @Test
    void startsItsHistoryAtTheStatusItWasCreatedWith() {
        TaskState created = TaskState.builder("proj", "/wt", TaskStatus.NEW)
                .lastActiveTimestamp(1_000).build();

        assertThat(created.history()).containsExactly(new StatusChange(TaskStatus.NEW, 1_000, null));
    }

    @Test
    void recordsEveryMoveInTheOrderItHappened() {
        TaskState shipped = TaskState.builder("proj", "/wt", TaskStatus.NEW).lastActiveTimestamp(1_000).build()
                .withStatus(TaskStatus.IN_PROGRESS, "working", 2_000)
                .withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000)
                .withStatus(TaskStatus.SHIPPING, "shipping", 2_000);

        assertThat(shipped.history()).extracting(StatusChange::status)
                .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING,
                        TaskStatus.SHIPPING);
    }

    @Test
    void recordsNothingForAKeepAliveBecauseTheStatusDidNotChange() {
        TaskState working = TaskState.builder("proj", "/wt", TaskStatus.NEW).lastActiveTimestamp(1_000).build()
                .withStatus(TaskStatus.IN_PROGRESS, "working", 2_000);

        TaskState stillWorking = working.touched(2_000).touched(3_000).touched(4_000);

        assertThat(stillWorking.history()).extracting(StatusChange::status)
                .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS);
    }

    @Test
    void dropsTheOldestMovesOnceFiftyAreRecordedSoStateJsonStaysSmall() {
        List<StatusChange> fifty = IntStream.range(0, 50)
                .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null)).toList();
        TaskState full = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fifty).build();

        TaskState moved = full.withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000);

        assertThat(moved.history()).hasSize(50);
        assertThat(moved.history().getFirst().at()).isEqualTo(1);
        assertThat(moved.history().getLast().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void reportsTimeInTheCurrentStatusRatherThanTimeSinceTheLastKeepAlive() {
        TaskState working = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000, null))).build();

        TaskState keptAlive = working.touched(2_000);

        assertThat(keptAlive.statusSince()).isEqualTo(1_000);
        assertThat(keptAlive.lastActiveTimestamp()).isEqualTo(2_000);
    }

    @Test
    void reportsTheActivityStampForATaskWrittenBeforeHistoryExisted() {
        TaskState legacy = new TaskState(List.of(TaskRepo.of("proj", "/wt")), TaskStatus.REVIEW_PENDING, 5_000, null,
                "a1", null, null, null, 0, 0, 0, 0, null, null, null, false, null, null, null, null, null, null, List.of(), PlanGate.OPEN, null, null, null);

        assertThat(legacy.history()).isEmpty();
        assertThat(legacy.statusSince()).isEqualTo(5_000);
    }

    @Test
    void opensANewPollingWindowForEveryRoundShippedOntoTheSameRequest() {
        TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/mr/9").mrCreatedAt(1_000L).lastPolledAt(9_000L).build();

        TaskState round = polling.withReviewRound("https://host/mr/9", 2_000L);

        assertThat(round.mrCreatedAt()).isEqualTo(2_000L);
        assertThat(round.lastPolledAt()).isZero();
        assertThat(round.primary().mrUrl()).isEqualTo("https://host/mr/9");
    }

    @Test
    void recordsSomethingDoneToTheTaskEvenWhenItsStatusDoesNotChange() {
        TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .history(List.of(new StatusChange(TaskStatus.CI_POLLING, 1_000, null))).build();

        TaskState again = polling.withStatus(TaskStatus.CI_POLLING, "review request: https://host/mr/9", true, 2_000);

        assertThat(again.history()).hasSize(2);
        assertThat(again.statusSince()).isEqualTo(again.history().getLast().at());
    }

    @Test
    void dropsARepeatedStatusATaskOnlyKeepsSayingAboutItself() {
        TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .history(List.of(new StatusChange(TaskStatus.CI_POLLING, 1_000, null))).build();

        TaskState again = polling.withStatus(TaskStatus.CI_POLLING, "still polling", 2_000);

        assertThat(again.history()).hasSize(1);
    }

    @Test
    void keepsTheStatusStampOfALegacyTaskWhenTheAgentOnlyPingsItsKeepAlive() {
        TaskState legacy = new TaskState(List.of(TaskRepo.of("proj", "/wt")), TaskStatus.IN_PROGRESS,
                1_700_000_000_000L, "working", "a1", null, null, null, 0, 0, 0, 0, null, null, null, false, null,
                null, null, List.of(), null, null, List.of(), PlanGate.OPEN, null, null, null);

        TaskState afterKeepAlive = legacy.touched(1_700_000_001_000L);

        assertThat(afterKeepAlive.statusSince()).isEqualTo(1_700_000_000_000L);
        assertThat(afterKeepAlive.lastActiveTimestamp()).isEqualTo(1_700_000_001_000L);
    }

    @Test
    void keepsWhatTheHostSaidAboutTheChecksWhenTheTaskGoesOnMoving() {
        TaskState red = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()
                .withChecksRead("failed");

        TaskState reported = red.withStatus(TaskStatus.CI_FAILED, "build broken", 2_000);

        assertThat(reported.pipelineStatus()).isEqualTo("failed");
    }

    @Test
    void dropsTheSilenceStampAsSoonAsTheAgentReportsAnything() {
        TaskState silent = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withSilentSince(1_000, "waiting for input");

        assertThat(silent.touched(2_000).agentIsSilent()).isFalse();
    }

    @Test
    void readsAStateFileWrittenBeforeATaskCouldSpanRepositories() {
        String legacy = """
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
                }""";

        TaskState task = new JsonMapper().readValue(legacy, TaskState.class);

        assertThat(task.repos()).hasSize(1);
        assertThat(task.project()).isEqualTo("demo");
        assertThat(task.worktreePath()).isEqualTo("/wt/ABC-1-demo");
        assertThat(task.remoteUrl()).isEqualTo("git@host:g/p.git");
        assertThat(task.mrUrl()).isEqualTo("https://host/mr/9");
        assertThat(task.deployCommit()).isEqualTo("cafebabe1234");
        assertThat(task.status()).isEqualTo(TaskStatus.CI_POLLING);
        assertThat(task.alias()).isEqualTo("a1");
    }

    @Test
    void writesRepositoriesAsAListAndReadsThemBack() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("php", "/wt/ABC-1-php"),
                        TaskRepo.of("java", "/wt/ABC-1-java")), TaskStatus.IN_PROGRESS)
                .alias("a1").title("contract change").build();
        JsonMapper mapper = new JsonMapper();

        String json = mapper.writeValueAsString(task);
        TaskState reread = mapper.readValue(json, TaskState.class);

        assertThat(json).contains("\"repos\"").doesNotContain("\"worktreePath\":\"/wt/ABC-1-php\",\"project\"");
        assertThat(reread.projects()).containsExactly("php", "java");
        assertThat(reread.worktreePath()).isEqualTo("/wt/ABC-1-php");
    }

    @Test
    void keepsAReviewRequestOnTheRepositoryItBelongsTo() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.REVIEW_PENDING).build();

        TaskState shipped = task.withReviewRound("php", "https://host/php/mr/1", 2_000)
                .withReviewRound("java", "https://host/java/mr/2", 2_000);

        assertThat(shipped.repo("php").orElseThrow().mrUrl()).isEqualTo("https://host/php/mr/1");
        assertThat(shipped.repo("java").orElseThrow().mrUrl()).isEqualTo("https://host/java/mr/2");
        assertThat(shipped.hasReviewRequest()).isTrue();
    }

    @Test
    void keepsAMergeCommitOnTheRepositoryItLandedIn() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.DEPLOYED).build();

        TaskState deployed = task.withDeployCommit("java", "f00d1234");

        assertThat(deployed.repo("java").orElseThrow().deployCommit()).isEqualTo("f00d1234");
        assertThat(deployed.repo("php").orElseThrow().deployCommit()).isNull();
    }

    @Test
    void keepsAKnownRequestAgeWhenAReadCannotSayWhenItWasOpened() {
        TaskState stamped = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").requestOpenedAt(1_700_000_000_000L).build();

        assertThat(stamped.withRequestOpenedAt(0).requestOpenedAt()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void datesARequestItJustOpenedByTheClockInsteadOfLeavingTheAgeBlank() {
        TaskState stamped = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").requestOpenedAt(1_700_000_000_000L).build();

        long opened = stamped.withMrUrl("http://mr/2", 1_700_000_005_000L).requestOpenedAt();

        assertThat(opened).isEqualTo(1_700_000_005_000L);
        assertThat(stamped.withMrUrl("http://mr/1", 1_700_000_005_000L).requestOpenedAt())
                .isEqualTo(1_700_000_000_000L);
    }

    @Test
    void forgetsWhatWasReadAboutARequestWhenTheTaskIsPointedAtAnotherOne() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).mrUrl("https://host/mr/1")
                .approved(true).pipelineStatus("success").requestOpenedAt(1_700_000_000_000L).build();

        TaskState relinked = task.withMrUrl("https://host/mr/2", 1_700_000_005_000L);

        assertThat(relinked.approved()).isNull();
        assertThat(relinked.pipelineStatus()).isNull();
        assertThat(relinked.requestOpenedAt()).isEqualTo(1_700_000_005_000L);
    }

    @Test
    void keepsWhatWasReadWhenTheLinkHasNotChanged() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).mrUrl("https://host/mr/1")
                .approved(true).pipelineStatus("success").build();

        assertThat(task.withMrUrl("https://host/mr/1", 2_000).approved()).isTrue();
    }

    @Test
    void reportsItsHistoryAsCappedOnlyOnceFiftyStepsAreHeld() {
        List<StatusChange> fortyNine = IntStream.range(0, 49)
                .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null)).toList();
        List<StatusChange> fifty = IntStream.range(0, 50)
                .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null)).toList();

        assertThat(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fortyNine).build()
                .historyAtCap()).isFalse();
        assertThat(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fifty).build()
                .historyAtCap()).isTrue();
    }

    @Test
    void keepsAllFiftyMovesWhenTheFiftiethIsRecorded() {
        List<StatusChange> fortyNine = IntStream.range(0, 49)
                .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null)).toList();
        TaskState almostFull = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fortyNine).build();

        TaskState moved = almostFull.withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000);

        assertThat(moved.history()).hasSize(50);
        assertThat(moved.history().getFirst().at()).isZero();
    }

    @Test
    void countsOneRoundForEveryEntryIntoCiPolling() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1, null),
                        new StatusChange(TaskStatus.CI_POLLING, 2, null),
                        new StatusChange(TaskStatus.IN_PROGRESS, 3, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 4, null),
                        new StatusChange(TaskStatus.CI_POLLING, 5, null))).build();

        assertThat(task.rounds()).isEqualTo(2);
    }

    @Test
    void stampsWhoCausedTheLastStepAndNoOtherStep() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1, ActionOrigin.BOARD),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2, null))).build();

        TaskState stamped = task.withLastChangeOrigin(ActionOrigin.TRACKER);

        assertThat(stamped.history()).extracting(StatusChange::origin)
                .containsExactly(ActionOrigin.BOARD, ActionOrigin.TRACKER);
    }

    @Test
    void stampsNobodyOnATaskThatHasNoStepYet() {
        TaskState task = new JsonMapper().readValue("""
                {"project":"demo","worktreePath":"/wt","status":"NEW","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}""", TaskState.class);

        assertThat(task.withLastChangeOrigin(ActionOrigin.BOARD)).isSameAs(task);
    }

    @Test
    void countsEveryRefusalPerGuardAcrossRefusals() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

        TaskState refused = task.refused(Guard.STATUS_REPORT).refused(Guard.STATUS_REPORT)
                .refused(Guard.GIT_STATE);

        assertThat(refused.refusals()).containsEntry(Guard.STATUS_REPORT, 2).containsEntry(Guard.GIT_STATE, 1);
    }

    @Test
    void clearsTheSilenceReasonWhenTheSilenceStampIsZero() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

        TaskState silent = task.withSilentSince(5, "no report");
        TaskState spoke = silent.withSilentSince(0, "no report");

        assertThat(silent.silentBecause()).isEqualTo("no report");
        assertThat(spoke.silentBecause()).isNull();
        assertThat(spoke.agentIsSilent()).isFalse();
    }

    @Test
    void takesTheProjectDefaultBaseBranchOnlyWhereTheTaskNamedNone() {
        TaskState own = TaskState.builder("proj", "/wt", TaskStatus.NEW).baseBranch("release").build();
        TaskState blank = TaskState.builder("proj", "/wt", TaskStatus.NEW).baseBranch(" ").build();
        TaskState none = TaskState.builder("proj", "/wt", TaskStatus.NEW).build();

        assertThat(own.baseBranchOr("main")).isEqualTo("release");
        assertThat(blank.baseBranchOr("main")).isEqualTo("main");
        assertThat(none.baseBranchOr("main")).isEqualTo("main");
    }

    @Test
    void followsTheConfigDefaultForAutoReviewOnlyWhereTheTaskSetNone() {
        TaskState on = TaskState.builder("proj", "/wt", TaskStatus.NEW).autoReview(true).build();
        TaskState off = TaskState.builder("proj", "/wt", TaskStatus.NEW).autoReview(false).build();
        TaskState unset = TaskState.builder("proj", "/wt", TaskStatus.NEW).build();

        assertThat(on.autoReviewEnabled(false)).isTrue();
        assertThat(off.autoReviewEnabled(true)).isFalse();
        assertThat(unset.autoReviewEnabled(true)).isTrue();
        assertThat(unset.autoReviewEnabled(false)).isFalse();
    }

    @Test
    void findsTheReviewRequestOfOneProjectAndNoneForAProjectWithoutOne() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("php", "/wt/php").withMrUrl("https://host/php/mr/1"),
                TaskRepo.of("java", "/wt/java"), TaskRepo.of("web", "/wt/web").withMrUrl(" ")),
                TaskStatus.REVIEW_PENDING).build();

        assertThat(task.reviewRequestOf("php")).contains("https://host/php/mr/1");
        assertThat(task.reviewRequestOf("java")).isEmpty();
        assertThat(task.reviewRequestOf("web")).isEmpty();
    }

    @Test
    void recordsOneRoundForAShipAcrossRepositoriesCarryingTheSessionRepositoryLink() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.REVIEW_PENDING).build();

        TaskState shipped = task.withReviewRound(Map.of("php", "https://host/php/mr/1",
                "java", "https://host/java/mr/2"), 2_000);

        assertThat(shipped.repo("php").orElseThrow().mrUrl()).isEqualTo("https://host/php/mr/1");
        assertThat(shipped.repo("java").orElseThrow().mrUrl()).isEqualTo("https://host/java/mr/2");
        assertThat(shipped.mrUrl()).isEqualTo("https://host/php/mr/1");
    }

    @Test
    void leavesTheTaskAloneWhenAShipLandedInNoRepository() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

        assertThat(task.withReviewRound(Map.of(), 2_000)).isSameAs(task);
    }

    @Test
    void addsMeteredUsageToWhatWasMeteredBefore() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withUsageAdded(TokenUsage.ofCall(10, 0, 5, 0)).withUsageAdded(TokenUsage.ofCall(20, 0, 5, 0));

        assertThat(task.usageOrNone().calls()).isEqualTo(2);
        assertThat(task.usageOrNone().inputTokens()).isEqualTo(30);
    }

    @Test
    void keepsTheLastPollTheRemoteUrlAndTheDeployCommitItWasGiven() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.DEPLOYED).build();

        TaskState noted = task.withLastPolledAt(42).withRemoteUrl("java", "git@host:g/java.git")
                .withDeployCommit("cafe1234");

        assertThat(noted.lastPolledAt()).isEqualTo(42);
        assertThat(noted.repo("java").orElseThrow().remoteUrl()).isEqualTo("git@host:g/java.git");
        assertThat(noted.repo("php").orElseThrow().remoteUrl()).isNull();
        assertThat(noted.deployCommit()).isEqualTo("cafe1234");
    }

    @Test
    void listsOnlyTheRepositoriesThatCarryAProjectName() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of(null, "/wt/x"),
                TaskRepo.of(" ", "/wt/y")), TaskStatus.IN_PROGRESS).build();

        assertThat(task.projects()).containsExactly("php");
    }

    @Test
    void seedsALegacyTaskWithoutAnActivityStampAtTheMomentItWasFirstMoved() {
        TaskState legacy = new JsonMapper().readValue("""
                {"project":"demo","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":0,
                 "mrCreatedAt":0,"lastPolledAt":0}""", TaskState.class);

        TaskState moved = legacy.withStatus(TaskStatus.IN_PROGRESS, "ping", true, 1_700_000_005_000L);

        assertThat(moved.history().getFirst().at()).isEqualTo(1_700_000_005_000L);
    }

    @Test
    void readsBackEveryFieldItWroteIncludingWhatAnEarlierFileCouldOmit() {
        JsonMapper mapper = new JsonMapper();
        TaskState written = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()
                .withChecksUnread()
                .withProbes(List.of(Probe.specified(Probe.Kind.COMMAND, List.of("make"), 0)))
                .refused(Guard.GIT_STATE);

        TaskState read = mapper.readValue(mapper.writeValueAsString(written), TaskState.class);

        assertThat(read.pipelineUnread()).isTrue();
        assertThat(read.probes()).hasSize(1);
        assertThat(read.refusals()).containsEntry(Guard.GIT_STATE, 1);
        assertThat(read.history()).hasSize(1);
    }

    @Test
    void recordsWhatJagtFoundAboutATaskWithoutCountingItAsTheAgentMoving() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).lastActiveTimestamp(1_000).build();
        AgentSpend spend = AgentSpend.NONE.plus(TokenUsage.ofCall(10, 0, 5, 0), "log", 7);
        GitTrust trust = GitTrust.of(Map.of("main", "abc"));
        List<Probe> probes = List.of(Probe.specified(Probe.Kind.REF, List.of("main"), 0));

        TaskState found = task.withAgentSpend(spend).withGitTrust(trust).withProbes(probes)
                .withApproved(true).withChecksUnread();

        assertThat(found.agentSpend()).isEqualTo(spend);
        assertThat(found.gitTrust()).isEqualTo(trust);
        assertThat(found.probes()).isEqualTo(probes);
        assertThat(found.approved()).isTrue();
        assertThat(found.pipelineUnread()).isTrue();
        assertThat(found.lastActiveTimestamp()).isEqualTo(1_000);
    }

    @Test
    void readsAFileWrittenBeforeThePlanGateExistedAsNothingToWaitFor() {
        TaskState legacy = new JsonMapper().readValue("""
                {"project":"demo","worktreePath":"/wt","status":"NEW","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}""", TaskState.class);

        assertThat(legacy.planGate()).isEqualTo(PlanGate.OPEN);
    }

    @Test
    void addsTheRepositoryASessionAskedForAfterItsOwnAndEndsTheRequest() {
        TaskState asked = TaskState.builder("api", "/wt-api", TaskStatus.IN_PROGRESS).repoRequest("registry").build();

        TaskState added = asked.withRepo(TaskRepo.of("registry", "/wt-registry"));

        assertThat(added.projects()).containsExactly("api", "registry");
        assertThat(added.repoRequest()).isNull();
    }

    @Test
    void recordsARequestWithoutCountingItAsAgentActivity() {
        TaskState working = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(7_000).build();

        TaskState asked = working.withRepoRequest("registry");

        assertThat(asked.asksForARepository()).isTrue();
        assertThat(asked.lastActiveTimestamp()).isEqualTo(7_000);
    }
}
