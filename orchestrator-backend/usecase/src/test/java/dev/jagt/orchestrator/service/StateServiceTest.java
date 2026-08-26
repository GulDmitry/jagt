package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class StateServiceTest {

    private static StateService stateIn(Path root, Path stateFile) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(stateFile.toString())));
    }

    /** SSE and the TUI repaint both re-read on notification, so the event has to fire AFTER the write. */
    @Test
    void tellsListenersAboutAChangeOnlyAfterItIsOnDisk(@TempDir Path root) {
        Path stateFile = root.resolve("state.json");
        StateService state = stateIn(root, stateFile);
        List<String> seenByListener = new ArrayList<>();
        state.onChange(written -> seenByListener.add(
                written.tasks().keySet() + " on disk: " + state.tasks().keySet()));

        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        assertThat(seenByListener).containsExactly("[ABC-1] on disk: [ABC-1]");
    }

    @Test
    void resolvesACallerFromAnyRepositoryItsTaskWorksIn(@TempDir Path root) throws IOException {
        Path api = Files.createDirectories(root.resolve("ABC-1-alpha"));
        Path client = Files.createDirectories(root.resolve("ABC-1-beta"));
        StateService state = stateIn(root, root.resolve("state.json"));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("alpha", api.toString()),
                TaskRepo.of("beta", client.toString())), TaskStatus.IN_PROGRESS).build());

        assertThat(state.findByWorktree(client.toString())).get()
                .extracting(Map.Entry::getKey).isEqualTo("ABC-1");
    }

    @Test
    void keepsResolvingCallersWhenOneStateEntryHasNoWorktreePath(@TempDir Path root) throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-2-alpha"));
        Path stateFile = root.resolve("state.json");
        Files.writeString(stateFile, """
                {"tasks": {
                  "ABC-1": {"repos": [{"project": "alpha"}], "status": "NEW"},
                  "ABC-2": {"repos": [{"project": "alpha", "worktreePath": "%s"}], "status": "NEW"}
                }}
                """.formatted(worktree));

        assertThat(stateIn(root, stateFile).findByWorktree(worktree.toString())).get()
                .extracting(Map.Entry::getKey).isEqualTo("ABC-2");
    }

    @Test
    void staysQuietWhenAMutationChangedNothing(@TempDir Path root) {
        StateService state = stateIn(root, root.resolve("state.json"));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
        AtomicInteger changes = new AtomicInteger();
        state.onChange(written -> changes.incrementAndGet());

        boolean found = state.updateTask("NOPE-1", TaskState::touched);

        assertThat(found).isFalse();
        assertThat(changes).hasValue(0);
    }

    @Test
    void reportsEveryKindOfChangeIncludingARemoval(@TempDir Path root) {
        StateService state = stateIn(root, root.resolve("state.json"));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
        AtomicInteger changes = new AtomicInteger();
        state.onChange(written -> changes.incrementAndGet());

        state.updateTask("ABC-1", task -> task.withStatus(TaskStatus.IN_PROGRESS, "working"));
        state.removeTask("ABC-1");

        assertThat(changes).hasValue(2);
    }

    @Test
    void keepsWritingAndKeepsNotifyingWhenOneListenerThrows(@TempDir Path root) {
        StateService state = stateIn(root, root.resolve("state.json"));
        AtomicInteger secondListener = new AtomicInteger();
        state.onChange(written -> {
            throw new IllegalStateException("this listener is broken");
        });
        state.onChange(written -> secondListener.incrementAndGet());

        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        assertThat(state.task("ABC-1")).isPresent();
        assertThat(secondListener).hasValue(1);
    }

    /** The backup is the version BEFORE the last write, which is what makes it a recovery point. */
    @Test
    void keepsThePreviousVersionBesideTheStateFileOnEveryWrite(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        StateService state = stateIn(root, stateFile);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a2").build());

        assertThat(Files.readString(root.resolve("state.json.bak"))).contains("ABC-1").doesNotContain("ABC-2");
        assertThat(Files.readString(stateFile)).contains("ABC-1", "ABC-2");
    }

    @Test
    void recoversEveryTaskFromTheBackupWhenTheStateFileIsUnreadable(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        StateService state = stateIn(root, stateFile);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a2").build());
        Files.writeString(stateFile, "{\"tasks\": {\"ABC-1\": {truncated…");

        var tasks = stateIn(root, stateFile).tasks();

        assertThat(tasks).containsOnlyKeys("ABC-1");
        assertThat(Files.exists(root.resolve("state.json.corrupt"))).isTrue();
        assertThat(Files.readString(root.resolve("state.json.corrupt"))).contains("truncated");
    }

    /**
     * Recovery moves the unreadable file aside, leaving NO state file: without writing the recovered tasks back,
     * the very next read answers "no tasks" and the write after it buries the backup.
     */
    @Test
    void putsTheRecoveredTasksBackSoTheNextReaderStillFindsThem(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        StateService state = stateIn(root, stateFile);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a2").build());
        Files.writeString(stateFile, "{\"tasks\": {\"ABC-1\": {truncated…");

        stateIn(root, stateFile).tasks();

        assertThat(stateIn(root, stateFile).tasks()).containsOnlyKeys("ABC-1");
    }

    /** Starting empty is the one unacceptable outcome: the next write buries a file a human could salvage. */
    @Test
    void refusesToStartWithAnEmptyTaskListOverAnUnreadableStateFile(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        Files.writeString(stateFile, "this is not json");

        assertThatThrownBy(() -> stateIn(root, stateFile).tasks())
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("no usable backup");
    }

    @Test
    void answersFromTheLastParseWhileTheFileOnDiskHasNotMoved(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        StateService state = stateIn(root, stateFile);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
        FileTime written = Files.getLastModifiedTime(stateFile);
        Files.writeString(stateFile, "x".repeat((int) Files.size(stateFile)));
        Files.setLastModifiedTime(stateFile, written);

        assertThat(state.tasks()).containsOnlyKeys("ABC-1");
    }

    @Test
    void picksUpAStateFileThatSomethingElseRewrote(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        StateService state = stateIn(root, stateFile);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        Files.writeString(stateFile, """
                {"tasks":{"ABC-2":{"project":"proj","worktreePath":"/wt","status":"NEW","alias":"a2"}}}""");

        assertThat(state.tasks()).containsOnlyKeys("ABC-2");
    }

    @Test
    void keepsItsOwnCopyWhenACallerMutatesTheTasksItWasHanded(@TempDir Path root) {
        StateService state = stateIn(root, root.resolve("state.json"));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        state.tasks().clear();

        assertThat(state.tasks()).containsOnlyKeys("ABC-1");
    }

    /** A derived accessor must not become a persisted field: Jackson once wrote isNone() as "none":false. */
    @Test
    void writesOnlyRealStateForATaskThatHasSpentTokens(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        StateService state = stateIn(root, stateFile);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .usage(TokenUsage.ofCall(25_000, 100, 170, 0.05)).build());

        String written = Files.readString(stateFile);

        assertThat(written).contains("\"inputTokens\" : 25000", "\"outputTokens\" : 170");
        assertThat(written).doesNotContain("none");
    }

    /**
     * The new primitive longs are simply absent from such a file. Failing the load instead of defaulting them
     * stranded every task and left /state and the dashboard empty.
     */
    @Test
    void loadsAStateFileWrittenBeforeTheAutoReviewFieldsExisted(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        Files.writeString(stateFile, """
                {"tasks":{"ABC-1":{"project":"proj","worktreePath":"/wt","status":"CI_POLLING",
                "lastActiveTimestamp":123,"alias":"a1","mrUrl":"http://mr/1"}}}""");
        StateService state = stateIn(root, stateFile);

        var task = state.task("ABC-1").orElseThrow();

        assertThat(task.status()).isEqualTo(TaskStatus.CI_POLLING);
        assertThat(task.mrUrl()).isEqualTo("http://mr/1");
        assertThat(task.mrCreatedAt()).isZero();
        assertThat(task.lastPolledAt()).isZero();
        assertThat(task.autoReview()).isNull();
    }

    @Test
    void resolvesCallerTaskWhenCallerReportsPhysicalPathOfSymlinkedWorktree(@TempDir Path root) throws IOException {
        StateService state = stateIn(root, root.resolve("state.json"));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.NEW).alias("a1").build());
        String physicalCallerCwd = root.toRealPath().toString();

        var found = state.findByWorktree(physicalCallerCwd);

        assertThat(found).map(Map.Entry::getKey).contains("ABC-1");
    }

    @Test
    void recordsWhoAskedForEachStepATaskTakes(@TempDir Path root) {
        StateService state = stateIn(root, root.resolve("state.json"));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        OriginContext.as(ActionOrigin.BOARD,
                () -> state.updateTask("ABC-1", task -> task.withStatus(TaskStatus.SHIPPING, "shipping")));

        assertThat(state.task("ABC-1").orElseThrow().history())
                .extracting(StatusChange::status, StatusChange::origin)
                .containsExactly(tuple(TaskStatus.NEW, null), tuple(TaskStatus.SHIPPING, ActionOrigin.BOARD));
    }

    @Test
    void leavesTheEarlierStepAloneWhenAKeepAliveChangesNothing(@TempDir Path root) {
        StateService state = stateIn(root, root.resolve("state.json"));
        OriginContext.as(ActionOrigin.BOARD,
                () -> state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build()));

        OriginContext.as(ActionOrigin.MCP, () -> state.updateTask("ABC-1", TaskState::touched));

        assertThat(state.task("ABC-1").orElseThrow().history())
                .extracting(StatusChange::origin)
                .containsExactly(ActionOrigin.BOARD);
    }

    /**
     * A task written before history existed has its status reconstructed on the next write. Signing that
     * reconstruction would credit whoever happened to write next with a status a human reached days ago.
     */
    @Test
    void doesNotSignTheStatusItReconstructsForATaskThatPredatesHistory(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        Files.writeString(stateFile, """
                {"tasks":{"ABC-1":{"project":"proj","worktreePath":"/wt","status":"APPROVED","alias":"a1",
                "lastActiveTimestamp":1000}}}""");
        StateService state = stateIn(root, stateFile);

        OriginContext.as(ActionOrigin.MCP, () -> state.updateTask("ABC-1", TaskState::touched));

        assertThat(state.task("ABC-1").orElseThrow().history())
                .extracting(StatusChange::status, StatusChange::origin)
                .containsExactly(tuple(TaskStatus.APPROVED, null));
    }

    @Test
    void forgetsTaskWhenItIsRemoved(@TempDir Path root) {
        StateService state = stateIn(root, root.resolve("state.json"));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1").build());

        boolean removed = state.removeTask("ABC-1");

        assertThat(removed).isTrue();
        assertThat(state.task("ABC-1")).isEmpty();
    }
}
