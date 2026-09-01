package dev.jagt.orchestrator.task;

import dev.jagt.orchestrator.flow.TaskStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
                .withStatus(TaskStatus.IN_PROGRESS, "working")
                .withStatus(TaskStatus.REVIEW_PENDING, "done")
                .withStatus(TaskStatus.SHIPPING, "shipping");

        assertThat(shipped.history()).extracting(StatusChange::status)
                .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING,
                        TaskStatus.SHIPPING);
    }

    @Test
    void recordsNothingForAKeepAliveBecauseTheStatusDidNotChange() {
        TaskState working = TaskState.builder("proj", "/wt", TaskStatus.NEW).lastActiveTimestamp(1_000).build()
                .withStatus(TaskStatus.IN_PROGRESS, "working");

        TaskState stillWorking = working.touched().touched().touched();

        assertThat(stillWorking.history()).extracting(StatusChange::status)
                .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS);
    }

    @Test
    void dropsTheOldestMovesOnceFiftyAreRecordedSoStateJsonStaysSmall() {
        List<StatusChange> fifty = IntStream.range(0, 50)
                .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null)).toList();
        TaskState full = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fifty).build();

        TaskState moved = full.withStatus(TaskStatus.REVIEW_PENDING, "done");

        assertThat(moved.history()).hasSize(50);
        assertThat(moved.history().getFirst().at()).isEqualTo(1);
        assertThat(moved.history().getLast().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void reportsTimeInTheCurrentStatusRatherThanTimeSinceTheLastKeepAlive() {
        TaskState working = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000, null))).build();

        TaskState keptAlive = working.touched();

        assertThat(keptAlive.statusSince()).isEqualTo(1_000);
        assertThat(keptAlive.lastActiveTimestamp()).isGreaterThan(1_000);
    }

    @Test
    void reportsTheActivityStampForATaskWrittenBeforeHistoryExisted() {
        TaskState legacy = legacyTask(TaskStatus.REVIEW_PENDING, 5_000, null, null);

        assertThat(legacy.history()).isEmpty();
        assertThat(legacy.statusSince()).isEqualTo(5_000);
    }

    @Test
    void opensANewPollingWindowForEveryRoundShippedOntoTheSameRequest() {
        TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/mr/9").mrCreatedAt(1_000L).lastPolledAt(9_000L).build();

        TaskState round = polling.withReviewRound("https://host/mr/9");

        assertThat(round.mrCreatedAt()).isGreaterThan(1_000L);
        assertThat(round.lastPolledAt()).isZero();
        assertThat(round.primary().mrUrl()).isEqualTo("https://host/mr/9");
    }

    @Test
    void recordsSomethingDoneToTheTaskEvenWhenItsStatusDoesNotChange() {
        TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .history(List.of(new StatusChange(TaskStatus.CI_POLLING, 1_000, null))).build();

        TaskState again = polling.withStatus(TaskStatus.CI_POLLING, "review request: https://host/mr/9", true);

        assertThat(again.history()).hasSize(2);
        assertThat(again.statusSince()).isEqualTo(again.history().getLast().at());
    }

    @Test
    void dropsARepeatedStatusATaskOnlyKeepsSayingAboutItself() {
        TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .history(List.of(new StatusChange(TaskStatus.CI_POLLING, 1_000, null))).build();

        TaskState again = polling.withStatus(TaskStatus.CI_POLLING, "still polling");

        assertThat(again.history()).hasSize(1);
    }

    @Test
    void keepsTheStatusStampOfALegacyTaskWhenTheAgentOnlyPingsItsKeepAlive() {
        long anHourAgo = System.currentTimeMillis() - 3_600_000L;
        TaskState legacy = legacyTask(TaskStatus.IN_PROGRESS, anHourAgo, "working", List.of());

        TaskState afterKeepAlive = legacy.touched();

        assertThat(afterKeepAlive.statusSince()).isEqualTo(anHourAgo);
        assertThat(afterKeepAlive.lastActiveTimestamp()).isGreaterThan(anHourAgo);
    }

    @Test
    void keepsWhatTheHostSaidAboutTheChecksWhenTheTaskGoesOnMoving() {
        TaskState red = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()
                .withPipelineStatus("failed");

        TaskState reported = red.withStatus(TaskStatus.CI_FAILED, "build broken");

        assertThat(reported.pipelineStatus()).isEqualTo("failed");
    }

    @Test
    void dropsTheSilenceStampAsSoonAsTheAgentReportsAnything() {
        TaskState silent = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withSilentSince(1_000, "waiting for input");

        assertThat(silent.touched().agentIsSilent()).isFalse();
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

        TaskState shipped = task.withReviewRound("php", "https://host/php/mr/1")
                .withReviewRound("java", "https://host/java/mr/2");

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

        assertThat(stamped.withMrUrl("http://mr/2").requestOpenedAt())
                .isCloseTo(System.currentTimeMillis(), within(60_000L));
        assertThat(stamped.withMrUrl("http://mr/1").requestOpenedAt()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void forgetsWhatWasReadAboutARequestWhenTheTaskIsPointedAtAnotherOne() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).mrUrl("https://host/mr/1")
                .approved(true).pipelineStatus("success").requestOpenedAt(1_700_000_000_000L).build();

        TaskState relinked = task.withMrUrl("https://host/mr/2");

        assertThat(relinked.approved()).isNull();
        assertThat(relinked.pipelineStatus()).isNull();
        assertThat(relinked.requestOpenedAt()).isCloseTo(System.currentTimeMillis(), within(60_000L));
    }

    @Test
    void keepsWhatWasReadWhenTheLinkHasNotChanged() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).mrUrl("https://host/mr/1")
                .approved(true).pipelineStatus("success").build();

        assertThat(task.withMrUrl("https://host/mr/1").approved()).isTrue();
    }

    private static TaskState legacyTask(TaskStatus status, long lastActive, String message,
                                        List<StatusChange> history) {
        return new TaskState(List.of(TaskRepo.of("proj", "/wt")), status, lastActive, message, "a1", null,
                null, null, 0, 0, 0, 0, null, null, null, null, null, null, history);
    }
}
