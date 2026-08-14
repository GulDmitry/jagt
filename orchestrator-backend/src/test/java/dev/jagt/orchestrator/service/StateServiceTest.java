package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.ActionOrigin;
import dev.jagt.orchestrator.model.StatusChange;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.model.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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


    @Test
    void tellsListenersAboutAChangeOnlyAfterItIsOnDisk(@TempDir Path root) {
        // The guarantee both consumers need (SSE, TUI repaint): a listener that re-reads must see the change,
        // so it fires AFTER the write, not before it.
        Path stateFile = root.resolve("state.json");
        StateService state = stateIn(root, stateFile);
        List<String> seenByListener = new ArrayList<>();
        state.onChange(written -> seenByListener.add(
                written.tasks().keySet() + " on disk: " + state.tasks().keySet()));

        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        assertThat(seenByListener).containsExactly("[ABC-1] on disk: [ABC-1]");
    }

    @Test
    void staysQuietWhenAMutationChangedNothing(@TempDir Path root) {
        StateService state = stateIn(root, root.resolve("state.json"));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
        AtomicInteger changes = new AtomicInteger();
        state.onChange(written -> changes.incrementAndGet());

        boolean found = state.updateTask("NOPE-1", TaskState::touched);

        assertThat(found).isFalse();
        assertThat(changes).hasValue(0);       // a repaint per no-op update is noise, not information
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

        assertThat(state.task("ABC-1")).isPresent();     // the mutation is what matters; listeners are not
        assertThat(secondListener).hasValue(1);
    }

    @Test
    void keepsThePreviousVersionBesideTheStateFileOnEveryWrite(@TempDir Path root) throws IOException {
        Path stateFile = root.resolve("state.json");
        StateService state = stateIn(root, stateFile);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a2").build());

        // The backup is the version BEFORE the last write — that is what makes it a recovery point.
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

        assertThat(tasks).containsOnlyKeys("ABC-1");                  // whatever the backup still had
        assertThat(Files.exists(root.resolve("state.json.corrupt"))).isTrue();
        assertThat(Files.readString(root.resolve("state.json.corrupt"))).contains("truncated");
    }

    @Test
    void refusesToStartWithAnEmptyTaskListOverAnUnreadableStateFile(@TempDir Path root) throws IOException {
        // Silently starting empty is the one unacceptable outcome: the next write would overwrite the file
        // the human might still salvage by hand.
        Path stateFile = root.resolve("state.json");
        Files.writeString(stateFile, "this is not json");

        assertThatThrownBy(() -> stateIn(root, stateFile).tasks())
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("no usable backup");
    }

    @Test
    void writesOnlyRealStateForATaskThatHasSpentTokens(@TempDir Path root) throws IOException {
        // state.json is the SSOT: a derived accessor on TokenUsage must not become a persisted field. It did
        // once — Jackson picked up isNone() and wrote "none":false into every task's usage block.
        Path stateFile = root.resolve("state.json");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(stateFile.toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .usage(TokenUsage.ofCall(25_000, 100, 170, 0.05)).build());

        String written = Files.readString(stateFile);

        assertThat(written).contains("\"inputTokens\" : 25000", "\"outputTokens\" : 170");
        assertThat(written).doesNotContain("none");
    }

    @Test
    void loadsAStateFileWrittenBeforeTheAutoReviewFieldsExisted(@TempDir Path root) throws IOException {
        // A real state.json from before mrCreatedAt/lastPolledAt/autoReview were added: the new primitive
        // longs are simply absent. Jackson must default them to 0, not fail the whole load (which stranded
        // every task and left /state + the dashboard empty).
        Path stateFile = root.resolve("state.json");
        Files.writeString(stateFile, """
                {"tasks":{"ABC-1":{"project":"proj","worktreePath":"/wt","status":"CI_POLLING",
                "lastActiveTimestamp":123,"alias":"a1","mrUrl":"http://mr/1"}}}""");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(stateFile.toString())));

        var task = state.task("ABC-1").orElseThrow();

        assertThat(task.status()).isEqualTo(TaskStatus.CI_POLLING);
        assertThat(task.mrUrl()).isEqualTo("http://mr/1");
        assertThat(task.mrCreatedAt()).isZero();
        assertThat(task.lastPolledAt()).isZero();
        assertThat(task.autoReview()).isNull();
    }

    @Test
    void resolvesCallerTaskWhenCallerReportsPhysicalPathOfSymlinkedWorktree(@TempDir Path root) throws IOException {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
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
        OriginContext.as(ActionOrigin.CONSOLE,
                () -> state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build()));

        OriginContext.as(ActionOrigin.MCP, () -> state.updateTask("ABC-1", TaskState::touched));

        assertThat(state.task("ABC-1").orElseThrow().history())
                .extracting(StatusChange::origin)
                .containsExactly(ActionOrigin.CONSOLE);
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
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1").build());

        boolean removed = state.removeTask("ABC-1");

        assertThat(removed).isTrue();
        assertThat(state.task("ABC-1")).isEmpty();
    }
}
