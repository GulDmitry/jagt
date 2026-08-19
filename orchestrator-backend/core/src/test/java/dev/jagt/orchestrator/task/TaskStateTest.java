package dev.jagt.orchestrator.task;

import dev.jagt.orchestrator.flow.TaskStatus;

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

    /**
     * Another round shipped onto the same request never leaves CI_POLLING, so the row would be dropped as a
     * keep-alive — and a human reading the history would see one round where there were three.
     */
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
        // A task from a state.json written before history existed: statusSince used to fall back to
        // lastActiveTimestamp, which the keep-alive bumps, so an hour-old status read as "0m" forever.
        long anHourAgo = System.currentTimeMillis() - 3_600_000L;
        TaskState legacy = legacyTask(TaskStatus.IN_PROGRESS, anHourAgo, "working", List.of());

        TaskState afterKeepAlive = legacy.touched();

        assertThat(afterKeepAlive.statusSince()).isEqualTo(anHourAgo);
        assertThat(afterKeepAlive.lastActiveTimestamp()).isGreaterThan(anHourAgo);
    }

    /**
     * The migration that has to hold or the human loses their tasks: every state.json written before a task
     * could span repositories carries project/worktreePath/remoteUrl/mrUrl/deployCommit at the TOP level, and
     * those files are read on the next start. Silently dropping them is precisely what StateService's backup
     * machinery exists to prevent, so the old shape must map onto the single repo it always described.
     */
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

    /** A task written by the CURRENT code round-trips, and the file no longer carries the flat duplicates. */
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
        assertThat(reread.worktreePath()).isEqualTo("/wt/ABC-1-php");     // the agent's own repo is the first
    }

    /** Two repositories mean two review requests, and putting one on top of the other loses a diff. */
    @Test
    void keepsAReviewRequestOnTheRepositoryItBelongsTo() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.REVIEW_PENDING).build();

        TaskState shipped = task.withReviewRound("php", "https://host/php/mr/1")
                .withReviewRound("java", "https://host/java/mr/2");

        assertThat(shipped.repo("php").orElseThrow().mrUrl()).isEqualTo("https://host/php/mr/1");
        assertThat(shipped.repo("java").orElseThrow().mrUrl()).isEqualTo("https://host/java/mr/2");
        assertThat(shipped.hasReviewRequest()).isTrue();
        // The same rule for a deploy: each repo records the merge commit that `revert` would undo THERE.
        TaskState deployed = shipped.withDeployCommit("java", "f00d1234");
        assertThat(deployed.repo("java").orElseThrow().deployCommit()).isEqualTo("f00d1234");
        assertThat(deployed.repo("php").orElseThrow().deployCommit()).isNull();
    }

    /**
     * A task as an OLD state.json holds it: no history at all. The builder cannot express that (a null history
     * means "brand new", so it seeds one), which leaves the canonical constructor — and that is a row of
     * positional nulls that breaks on every new field, so it lives in exactly one place.
     */
    private static TaskState legacyTask(TaskStatus status, long lastActive, String message,
                                        List<StatusChange> history) {
        return new TaskState(List.of(TaskRepo.of("proj", "/wt")), status, lastActive, message, "a1", null,
                null, null, 0, 0, null, null, null, history);
    }
}
