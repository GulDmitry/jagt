package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class StateServiceTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()));

  @Test
  void tellsListenersAboutChangeOnlyAfterItIsOnDisk(@TempDir Path root) {
    Path stateFile = root.resolve("state.json");
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);
    List<String> seenByListener = new ArrayList<>();
    state.onChange(
        written ->
            seenByListener.add(written.tasks().keySet() + " on disk: " + state.tasks().keySet()));

    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

    assertThat(seenByListener).containsExactly("[ABC-1] on disk: [ABC-1]");
  }

  @Test
  void clearsRequestItsRepositorysRemoteDoesNotHoldOnTheFirstRead(@TempDir Path root) {
    Path stateFile = root.resolve("state.json");
    StateService writer =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);
    writer.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .repos(
                List.of(
                    new TaskRepo(
                        "proj",
                        "/wt",
                        "git@host:team/proj.git",
                        "https://host/other/proj/-/merge_requests/9")))
            .build());
    StateService reader =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);

    TaskState read = reader.task("ABC-1").orElseThrow();

    assertThat(read.reviewRequestOf("proj")).isEmpty();
  }

  @Test
  void resolvesCallerFromAnyRepositoryItsTaskWorksIn(@TempDir Path root) throws IOException {
    Path api = Files.createDirectories(root.resolve("ABC-1-alpha"));
    Path client = Files.createDirectories(root.resolve("ABC-1-beta"));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(
                    TaskRepo.of("alpha", api.toString()), TaskRepo.of("beta", client.toString())),
                TaskStatus.IN_PROGRESS)
            .build());

    assertThat(state.findByWorktree(client.toString()))
        .get()
        .extracting(Map.Entry::getKey)
        .isEqualTo("ABC-1");
  }

  @Test
  void keepsResolvingCallersWhenOneStateEntryHasNoWorktreePath(@TempDir Path root)
      throws IOException {
    Path worktree = Files.createDirectories(root.resolve("ABC-2-alpha"));
    Path stateFile = root.resolve("state.json");
    Files.writeString(
        stateFile,
        """
        {"tasks": {
          "ABC-1": {"repos": [{"project": "alpha"}], "status": "NEW"},
          "ABC-2": {"repos": [{"project": "alpha", "worktreePath": "%s"}], "status": "NEW"}
        }}
        """
            .formatted(worktree));

    assertThat(
            new StateService(
                    new JsonMapper(),
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults()
                            .withRoot(root.toString())
                            .withStateFile(stateFile.toString())),
                            reducer)
                .findByWorktree(worktree.toString()))
        .get()
        .extracting(Map.Entry::getKey)
        .isEqualTo("ABC-2");
  }

  @Test
  void staysQuietWhenMutationChangedNothing(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
    AtomicInteger changes = new AtomicInteger();
    state.onChange(written -> changes.incrementAndGet());

    boolean found = state.updateTask("NOPE-1", task -> task.touched(2_000));

    assertThat(found).isFalse();
    assertThat(changes).hasValue(0);
  }

  @Test
  void reportsEveryKindOfChangeIncludingRemoval(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
    AtomicInteger changes = new AtomicInteger();
    state.onChange(written -> changes.incrementAndGet());

    state.updateTask("ABC-1", task -> task.withStatus(TaskStatus.IN_PROGRESS, "working", 2_000));
    state.removeTask("ABC-1");

    assertThat(changes).hasValue(2);
  }

  @Test
  void keepsWritingAndKeepsNotifyingWhenOneListenerThrows(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    AtomicInteger secondListener = new AtomicInteger();
    state.onChange(
        written -> {
          throw new IllegalStateException("this listener is broken");
        });
    state.onChange(written -> secondListener.incrementAndGet());

    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

    assertThat(state.task("ABC-1")).isPresent();
    assertThat(secondListener).hasValue(1);
  }

  @Test
  void keepsThePreviousVersionBesideTheStateFileOnEveryWrite(@TempDir Path root)
      throws IOException {
    Path stateFile = root.resolve("state.json");
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

    state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a2").build());

    assertThat(Files.readString(root.resolve("state.json.bak")))
        .contains("ABC-1")
        .doesNotContain("ABC-2");
    assertThat(Files.readString(stateFile)).contains("ABC-1", "ABC-2");
  }

  @Test
  void recoversEveryTaskFromTheBackupWhenTheStateFileIsUnreadable(@TempDir Path root)
      throws IOException {
    Path stateFile = root.resolve("state.json");
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
    state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a2").build());
    Files.writeString(stateFile, "{\"tasks\": {\"ABC-1\": {truncated…");

    var tasks =
        new StateService(
                new JsonMapper(),
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withStateFile(stateFile.toString())),
                        reducer)
            .tasks();

    assertThat(tasks).containsOnlyKeys("ABC-1");
    assertThat(Files.exists(root.resolve("state.json.corrupt"))).isTrue();
    assertThat(Files.readString(root.resolve("state.json.corrupt"))).contains("truncated");
  }

  @Test
  void putsTheRecoveredTasksBackSoTheNextReaderStillFindsThem(@TempDir Path root)
      throws IOException {
    Path stateFile = root.resolve("state.json");
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
    state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a2").build());
    Files.writeString(stateFile, "{\"tasks\": {\"ABC-1\": {truncated…");

    new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer)
        .tasks();

    assertThat(
            new StateService(
                    new JsonMapper(),
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults()
                            .withRoot(root.toString())
                            .withStateFile(stateFile.toString())),
                            reducer)
                .tasks())
        .containsOnlyKeys("ABC-1");
  }

  @Test
  void refusesToStartWithAnEmptyTaskListOverAnUnreadableStateFile(@TempDir Path root)
      throws IOException {
    Path stateFile = root.resolve("state.json");
    Files.writeString(stateFile, "this is not json");

    assertThatThrownBy(
            () ->
                new StateService(
                        new JsonMapper(),
                        new OrchestratorPaths(
                            OrchestratorProperties.defaults()
                                .withRoot(root.toString())
                                .withStateFile(stateFile.toString())),
                                reducer)
                    .tasks())
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("no usable backup");
  }

  @Test
  void refusesToStartAndTouchesNeitherFileWhenStateAndBackupAreBothUnreadable(@TempDir Path root)
      throws IOException {
    Path stateFile = root.resolve("state.json");
    Files.writeString(stateFile, "this is not json");
    Files.writeString(root.resolve("state.json.bak"), "neither is this");

    assertThatThrownBy(
            () ->
                new StateService(
                        new JsonMapper(),
                        new OrchestratorPaths(
                            OrchestratorProperties.defaults()
                                .withRoot(root.toString())
                                .withStateFile(stateFile.toString())),
                                reducer)
                    .tasks())
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("no usable backup");
    assertThat(Files.readString(stateFile)).isEqualTo("this is not json");
    assertThat(Files.readString(root.resolve("state.json.bak"))).isEqualTo("neither is this");
  }

  @Test
  void leavesNoStagedCopyBehindWhenTheStateFileCannotBeReplaced(@TempDir Path root)
      throws IOException {
    Path stateFile = Files.createDirectories(root.resolve("state.json"));
    Files.writeString(stateFile.resolve("occupant"), "");
    Files.writeString(
        Files.createDirectories(root.resolve("state.json.corrupt")).resolve("occupant"), "");
    Files.writeString(root.resolve("state.json.bak"), "{\"tasks\":{}}");
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);

    assertThatThrownBy(
            () -> state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build()))
        .isInstanceOf(UncheckedIOException.class);
    try (var left = Files.list(root)) {
      assertThat(left)
          .map(path -> path.getFileName().toString())
          .containsExactlyInAnyOrder("state.json", "state.json.bak", "state.json.corrupt");
    }
  }

  @Test
  void answersFromTheLastParseWhileTheFileOnDiskHasNotMoved(@TempDir Path root) throws IOException {
    Path stateFile = root.resolve("state.json");
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
    FileTime written = Files.getLastModifiedTime(stateFile);
    Files.writeString(stateFile, "x".repeat((int) Files.size(stateFile)));
    Files.setLastModifiedTime(stateFile, written);

    assertThat(state.tasks()).containsOnlyKeys("ABC-1");
  }

  @Test
  void picksUpStateFileThatSomethingElseRewrote(@TempDir Path root) throws IOException {
    Path stateFile = root.resolve("state.json");
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

    Files.writeString(
        stateFile,
        """
        {"tasks":{"ABC-2":{"project":"proj","worktreePath":"/wt","status":"NEW","alias":"a2"}}}\
        """);

    assertThat(state.tasks()).containsOnlyKeys("ABC-2");
  }

  @Test
  void keepsItsOwnCopyWhenCallerMutatesTheTasksItWasHanded(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

    state.tasks().clear();

    assertThat(state.tasks()).containsOnlyKeys("ABC-1");
  }

  @Test
  void writesOnlyRealStateForTaskThatHasSpentTokens(@TempDir Path root) throws IOException {
    Path stateFile = root.resolve("state.json");
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .usage(TokenUsage.ofCall(25_000, 100, 170, 0.05))
            .build());

    String written = Files.readString(stateFile);

    assertThat(written).contains("\"inputTokens\" : 25000", "\"outputTokens\" : 170");
    assertThat(written).doesNotContain("none");
  }

  @Test
  void loadsStateFileWrittenBeforeTheAutoReviewFieldsExisted(@TempDir Path root)
      throws IOException {
    Path stateFile = root.resolve("state.json");
    Files.writeString(
        stateFile,
        """
        {"tasks":{"ABC-1":{"project":"proj","worktreePath":"/wt","status":"CI_POLLING",
        "lastActiveTimestamp":123,"alias":"a1","mrUrl":"http://mr/1"}}}\
        """);
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);

    var task = state.task("ABC-1").orElseThrow();

    assertThat(task.status()).isEqualTo(TaskStatus.CI_POLLING);
    assertThat(task.mrUrl()).isEqualTo("http://mr/1");
    assertThat(task.mrCreatedAt()).isZero();
    assertThat(task.lastPolledAt()).isZero();
    assertThat(task.autoReview()).isNull();
  }

  @Test
  void resolvesCallerTaskWhenCallerReportsPhysicalPathOfSymlinkedWorktree(@TempDir Path root)
      throws IOException {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.NEW).alias("a1").build());
    String physicalCallerCwd = root.toRealPath().toString();

    var found = state.findByWorktree(physicalCallerCwd);

    assertThat(found).map(Map.Entry::getKey).contains("ABC-1");
  }

  @Test
  void recordsWhoAskedForEachStepTaskTakes(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

    OriginContext.as(
        ActionOrigin.BOARD,
        () ->
            state.updateTask(
                "ABC-1", task -> task.withStatus(TaskStatus.SHIPPING, "shipping", 2_000)));

    assertThat(state.task("ABC-1").orElseThrow().history())
        .extracting(StatusChange::status, StatusChange::origin)
        .containsExactly(
            tuple(TaskStatus.NEW, null), tuple(TaskStatus.SHIPPING, ActionOrigin.BOARD));
  }

  @Test
  void recordsTheReasonOnTheStepItsRequestTook(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());

    OriginContext.because(
        "work done, tests green, ready for review",
        () ->
            state.updateTask(
                "ABC-1", task -> task.withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000)));

    assertThat(state.task("ABC-1").orElseThrow().history())
        .extracting(StatusChange::status, StatusChange::why)
        .containsExactly(
            tuple(TaskStatus.IN_PROGRESS, null),
            tuple(TaskStatus.REVIEW_PENDING, "work done, tests green, ready for review"));
  }

  @Test
  void keepsReasonGivenWithNoStepOffTheEarlierStep(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());

    OriginContext.because(
        "still on the parser", () -> state.updateTask("ABC-1", task -> task.touched(2_000)));

    assertThat(state.task("ABC-1").orElseThrow().history())
        .extracting(StatusChange::why)
        .containsOnlyNulls();
  }

  @Test
  void readsStepWrittenBeforeReasonsExistedAsGivingNone(@TempDir Path root) throws IOException {
    Path stateFile = root.resolve("state.json");
    Files.writeString(
        stateFile,
        """
        {"tasks":{"ABC-1":{"project":"proj","worktreePath":"/wt","status":"NEW","alias":"a1",
        "history":[{"status":"NEW","at":1000,"origin":"BOARD"}]}}}\
        """);
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);

    assertThat(state.task("ABC-1").orElseThrow().history())
        .containsExactly(new StatusChange(TaskStatus.NEW, 1000, ActionOrigin.BOARD));
  }

  @Test
  void leavesTheEarlierStepAloneWhenKeepAliveChangesNothing(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    OriginContext.as(
        ActionOrigin.BOARD,
        () ->
            state.putTask(
                "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build()));

    OriginContext.as(
        ActionOrigin.MCP, () -> state.updateTask("ABC-1", task -> task.touched(2_000)));

    assertThat(state.task("ABC-1").orElseThrow().history())
        .extracting(StatusChange::origin)
        .containsExactly(ActionOrigin.BOARD);
  }

  @Test
  void doesNotSignTheStatusItReconstructsForTaskThatPredatesHistory(@TempDir Path root)
      throws IOException {
    Path stateFile = root.resolve("state.json");
    Files.writeString(
        stateFile,
        """
        {"tasks":{"ABC-1":{"project":"proj","worktreePath":"/wt","status":"APPROVED","alias":"a1",
        "lastActiveTimestamp":1000}}}\
        """);
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);

    OriginContext.as(
        ActionOrigin.MCP, () -> state.updateTask("ABC-1", task -> task.touched(2_000)));

    assertThat(state.task("ABC-1").orElseThrow().history())
        .extracting(StatusChange::status, StatusChange::origin)
        .containsExactly(tuple(TaskStatus.APPROVED, null));
  }

  @Test
  void appliesAnUpdateToAnExistingTaskAndSaysSo(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

    boolean updated =
        state.updateTask("ABC-1", task -> task.withStatus(TaskStatus.DONE, "finished", 2_000));

    assertThat(updated).isTrue();
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DONE);
  }

  @Test
  void saysSoWhenAnUpdateNamesNoTask(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);

    assertThat(
            state.updateTask("ABC-1", task -> task.withStatus(TaskStatus.DONE, "finished", 2_000)))
        .isFalse();
  }

  @Test
  void resolvesAnAliasToItsOwnTaskIgnoringCase(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.NEW).alias("a1").build());
    state.putTask("ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.NEW).alias("a2").build());

    assertThat(state.canonicalTaskId("A2")).isEqualTo("ABC-2");
  }

  @Test
  void resolvesCallerToTheTaskWhoseWorktreeHoldsItNotTheFirstTask(@TempDir Path root)
      throws IOException {
    Path first = Files.createDirectories(root.resolve("ABC-1"));
    Path second = Files.createDirectories(root.resolve("ABC-2"));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", first.toString(), TaskStatus.NEW).build());
    state.putTask("ABC-2", TaskState.builder("proj", second.toString(), TaskStatus.NEW).build());

    assertThat(state.findByWorktree(second.toString()))
        .get()
        .extracting(Map.Entry::getKey)
        .isEqualTo("ABC-2");
  }

  @Test
  void answersFromTheParseOfStateFileWrittenByAnotherProcessWhileItHasNotMoved(@TempDir Path root)
      throws IOException {
    Path stateFile = root.resolve("state.json");
    String written =
        """
        {"tasks":{"ABC-2":{"project":"proj","worktreePath":"/wt","status":"NEW","alias":"a2"}}}\
        """;
    Files.writeString(stateFile, written);
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(stateFile.toString())),
                    reducer);
    FileTime stamp = Files.getLastModifiedTime(stateFile);
    state.tasks();
    Files.writeString(stateFile, "x".repeat(written.length()));
    Files.setLastModifiedTime(stateFile, stamp);

    assertThat(state.tasks()).containsOnlyKeys("ABC-2");
  }

  @Test
  void forgetsTaskWhenItIsRemoved(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1").build());

    boolean removed = state.removeTask("ABC-1");

    assertThat(removed).isTrue();
    assertThat(state.task("ABC-1")).isEmpty();
  }

  @Test
  void saysNothingWasRemovedWhereTheTaskIsNotThere(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1").build());

    boolean removed = state.removeTask("ABC-9");

    assertThat(removed).isFalse();
    assertThat(state.task("ABC-1")).isPresent();
  }

  @Test
  void findsNoTaskForDirectoryThatIsNoWorktreeAndDoesNotExist(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
                    reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt/gone", TaskStatus.DONE).build());

    assertThat(state.findByWorktree("/elsewhere/gone")).isEmpty();
  }

  @Test
  void reduceHandsTheStoredTaskToTheUpdateAndSaysWhetherTheTaskWasThere(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());

    boolean found = state.reduce("ABC-1", (taskReducer, task) -> task);
    boolean absent = state.reduce("ABC-2", (taskReducer, task) -> task);

    assertThat(found).isTrue();
    assertThat(absent).isFalse();
  }
}
