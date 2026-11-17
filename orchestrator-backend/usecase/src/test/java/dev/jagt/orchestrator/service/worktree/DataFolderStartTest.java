package dev.jagt.orchestrator.service.worktree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class DataFolderStartTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final Processes processes = mock(Processes.class);

  @TempDir Path root;

  @Test
  void createsTheTreeOfTheDataFolderOnFirstStart() {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.resolve("vault").toString());

    new DataFolderStart(new OrchestratorPaths(properties), processes, "").prepare();

    assertThat(root.resolve("vault/jagt")).isDirectory();
    assertThat(root.resolve("vault/projects")).isDirectory();
    assertThat(root.resolve("vault/tasks")).isDirectory();
    assertThat(root.resolve("vault/archive")).isDirectory();
    assertThat(root.resolve("vault/records")).isDirectory();
    assertThat(root.resolve("vault/memory")).isDirectory();
    assertThat(root.resolve("vault/logs")).isDirectory();
  }

  @Test
  void movesTheStateOfOlderVersionIntoTheDataFolderWithItsTasksAndRecordedWorktrees()
      throws Exception {
    Path recorded = root.resolve("beside-the-repository/ABC-1-demo");
    OrchestratorProperties older =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    new StateService(new JsonMapper(), new OrchestratorPaths(older), reducer, event -> {})
        .putTask(
            "ABC-1",
            TaskState.builder("demo", recorded.toString(), TaskStatus.IN_PROGRESS).build());
    Files.writeString(root.resolve("state.json.bak"), "{}");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.resolve("vault").toString()));

    new DataFolderStart(paths, processes, "").prepare();

    StateService resumed = new StateService(new JsonMapper(), paths, reducer, event -> {});
    assertThat(resumed.task("ABC-1"))
        .get()
        .extracting(TaskState::worktreePath)
        .isEqualTo(recorded.toString());
    assertThat(root.resolve("state.json")).doesNotExist();
    assertThat(root.resolve("vault/jagt/state.json.bak")).exists();
  }

  @Test
  void movesTheStateOfThePreviousDefaultDataFolderAndLeavesWhatTasksRecordedThere()
      throws Exception {
    Path previous = Files.createDirectories(root.resolve("home/.jagt"));
    Files.writeString(previous.resolve("state.json"), "tasks");
    Files.writeString(previous.resolve("finished.jsonl"), "f");
    Files.createDirectories(previous.resolve("repos/api"));
    Files.writeString(previous.resolve("repos/api/README"), "r");
    Files.createDirectories(previous.resolve("logs"));
    Files.writeString(previous.resolve("logs/jagt-backend.log"), "l");
    Files.createDirectories(previous.resolve("worktrees/api/ABC-1"));
    Files.createDirectories(previous.resolve("scratch/ABC-2"));
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.resolve("data").toString()));

    new DataFolderStart(paths, processes, previous.toString()).prepare();

    assertThat(root.resolve("data/jagt/state.json")).hasContent("tasks");
    assertThat(root.resolve("data/records/finished.jsonl")).hasContent("f");
    assertThat(root.resolve("data/projects/api/README")).hasContent("r");
    assertThat(root.resolve("data/logs/jagt-backend.log")).hasContent("l");
    assertThat(previous.resolve("state.json")).doesNotExist();
    assertThat(previous.resolve("worktrees/api/ABC-1")).isDirectory();
    assertThat(previous.resolve("scratch/ABC-2")).isDirectory();
  }

  @Test
  void movesTheFlatLayoutOfServerFolderIntoTheTreeInPlace() throws Exception {
    Path data = Files.createDirectories(root.resolve("data"));
    Files.writeString(data.resolve("state.json"), "tasks");
    Files.writeString(data.resolve("stops.jsonl"), "s");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()).withData(data.toString()));

    new DataFolderStart(paths, processes, "").prepare();

    assertThat(data.resolve("jagt/state.json")).hasContent("tasks");
    assertThat(data.resolve("records/stops.jsonl")).hasContent("s");
    assertThat(data.resolve("state.json")).doesNotExist();
  }

  @Test
  void movesNothingWhenTheDataFolderHoldsStateAlready() throws Exception {
    Files.writeString(root.resolve("state.json"), "old");
    Files.createDirectories(root.resolve("vault/jagt"));
    Files.writeString(root.resolve("vault/jagt/state.json"), "current");
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.resolve("vault").toString());

    new DataFolderStart(new OrchestratorPaths(properties), processes, "").prepare();

    assertThat(root.resolve("vault/jagt/state.json")).hasContent("current");
    assertThat(root.resolve("state.json")).hasContent("old");
  }

  @Test
  void leavesStateAloneWhoseFileWasNamedExplicitly() throws Exception {
    Files.writeString(root.resolve("state.json"), "old");
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.resolve("vault").toString())
            .withStateFile(root.resolve("state.json").toString());

    new DataFolderStart(new OrchestratorPaths(properties), processes, "").prepare();

    assertThat(root.resolve("state.json")).hasContent("old");
  }

  @Test
  void movesEveryFileAndFolderOfTheOldRootLayoutIntoItsPlaceInTheDataFolder() throws Exception {
    Files.writeString(root.resolve("state.json"), "tasks");
    Files.writeString(root.resolve("finished.jsonl"), "f");
    Files.writeString(root.resolve("stops.jsonl.1"), "s");
    Files.writeString(root.resolve("review-verdicts"), "v");
    Files.createDirectories(root.resolve("plan-baselines"));
    Files.writeString(root.resolve("plan-baselines/ABC-1.md"), "p");
    Files.createDirectories(root.resolve("ticket-texts"));
    Files.writeString(root.resolve("ticket-texts/ABC-1.md"), "t");
    Files.createDirectories(root.resolve("memory"));
    Files.writeString(root.resolve("memory/routing.md"), "m");
    Files.writeString(root.resolve("unrelated.txt"), "u");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    new DataFolderStart(paths, processes, "").prepare();

    assertThat(root.resolve("data/records/finished.jsonl")).hasContent("f");
    assertThat(root.resolve("data/records/stops.jsonl.1")).hasContent("s");
    assertThat(root.resolve("data/jagt/review-verdicts")).hasContent("v");
    assertThat(root.resolve("data/tasks/ABC-1/plan-baseline.md")).hasContent("p");
    assertThat(root.resolve("data/tasks/ABC-1/ticket-text.md")).hasContent("t");
    assertThat(root.resolve("data/memory/routing.md")).hasContent("m");
    assertThat(root.resolve("unrelated.txt")).exists();
    assertThat(root.resolve("finished.jsonl")).doesNotExist();
  }

  @Test
  void deletesTheOldMasterTokenBecauseEachStartDrawsAnew() throws Exception {
    Files.writeString(root.resolve("state.json"), "tasks");
    Files.writeString(root.resolve("master-token"), "t");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    try (FakeLogCapture log = new FakeLogCapture(DataFolderStart.class)) {
      new DataFolderStart(paths, processes, "").prepare();

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .contains("older master token deleted");
    }
    assertThat(root.resolve("master-token")).doesNotExist();
    assertThat(root.resolve("data/jagt/master-token")).doesNotExist();
  }

  @Test
  void warnsWithBothPathsWhereTheOlderFileIsKeptBecauseTheDataFolderHoldsOne() throws Exception {
    Files.writeString(root.resolve("state.json"), "tasks");
    Files.writeString(root.resolve("misses.jsonl"), "old");
    Files.createDirectories(root.resolve("data/records"));
    Files.writeString(root.resolve("data/records/misses.jsonl"), "current");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    try (FakeLogCapture log = new FakeLogCapture(DataFolderStart.class)) {
      new DataFolderStart(paths, processes, "").prepare();

      assertThat(log.events())
          .filteredOn(event -> event.getLevel() == Level.WARN)
          .singleElement()
          .satisfies(event ->
              assertThat(event.getKeyValuePairs())
                  .extracting(pair -> pair.key + "=" + pair.value)
                  .contains(
                      "from=" + root.resolve("misses.jsonl"),
                      "to=" + root.resolve("data/records/misses.jsonl")));
    }
  }

  @Test
  void movesNothingFromFolderHoldingNoState() throws Exception {
    Files.writeString(root.resolve("finished.jsonl"), "f");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    new DataFolderStart(paths, processes, "").prepare();

    assertThat(root.resolve("finished.jsonl")).hasContent("f");
    assertThat(root.resolve("data/records/finished.jsonl")).doesNotExist();
  }

  @Test
  void movesFromTheOneOlderFolderHoldingStateAndLeavesTheOthersAlone() throws Exception {
    Path previous = Files.createDirectories(root.resolve("home/.jagt"));
    Files.writeString(previous.resolve("finished.jsonl"), "previous");
    Files.writeString(root.resolve("state.json"), "tasks");
    Files.writeString(root.resolve("misses.jsonl"), "root");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    new DataFolderStart(paths, processes, previous.toString()).prepare();

    assertThat(root.resolve("data/records/misses.jsonl")).hasContent("root");
    assertThat(previous.resolve("finished.jsonl")).hasContent("previous");
    assertThat(root.resolve("data/records/finished.jsonl")).doesNotExist();
  }

  @Test
  void skipsEachOldFileWhoseNameTheDataFolderHoldsAndMovesTheOthers() throws Exception {
    Files.writeString(root.resolve("state.json"), "tasks");
    Files.writeString(root.resolve("misses.jsonl"), "old");
    Files.writeString(root.resolve("finished.jsonl"), "f");
    Files.createDirectories(root.resolve("data/records"));
    Files.writeString(root.resolve("data/records/misses.jsonl"), "current");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    new DataFolderStart(paths, processes, "").prepare();

    assertThat(root.resolve("data/records/misses.jsonl")).hasContent("current");
    assertThat(root.resolve("misses.jsonl")).hasContent("old");
    assertThat(root.resolve("data/records/finished.jsonl")).hasContent("f");
  }

  @Test
  void movesNothingOnTheSecondStartAndKeepsWhatTheFirstMoved() throws Exception {
    Files.writeString(root.resolve("state.json"), "tasks");
    Files.writeString(root.resolve("misses.jsonl"), "m");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    new DataFolderStart(paths, processes, "").prepare();
    Files.writeString(root.resolve("misses.jsonl"), "new at the root");

    new DataFolderStart(paths, processes, "").prepare();

    assertThat(root.resolve("data/records/misses.jsonl")).hasContent("m");
    assertThat(root.resolve("misses.jsonl")).hasContent("new at the root");
  }

  @Test
  void repairsEachExistingWorktreeOfMovedCloneThroughGit() throws Exception {
    Files.writeString(root.resolve("state.json"), "tasks");
    Path clone = root.resolve("data/projects/demo");
    Path kept = Files.createDirectories(root.resolve("beside/ABC-1"));
    Files.createDirectories(root.resolve("repos/demo/.git"));
    when(processes.run(
            clone, Duration.ofSeconds(30), List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(
            new Processes.Result(
                0,
                "worktree "
                    + clone
                    + "\n\nworktree "
                    + kept
                    + "\n\nworktree "
                    + root.resolve("gone")
                    + "\n",
                ""));
    when(processes.run(
            clone, Duration.ofSeconds(30), List.of("git", "worktree", "repair", kept.toString())))
        .thenReturn(new Processes.Result(0, "", ""));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    try (FakeLogCapture log = new FakeLogCapture(DataFolderStart.class)) {
      new DataFolderStart(paths, processes, "").prepare();

      assertThat(log.events()).extracting(ILoggingEvent::getLevel).doesNotContain(Level.WARN);
    }
    verify(processes)
        .run(clone, Duration.ofSeconds(30), List.of("git", "worktree", "repair", kept.toString()));
  }

  @Test
  void warnsWhereGitCannotRepairTheWorktreesOfMovedClone() throws Exception {
    Files.writeString(root.resolve("state.json"), "tasks");
    Files.createDirectories(root.resolve("repos/demo/.git"));
    when(processes.run(
            root.resolve("data/projects/demo"),
            Duration.ofSeconds(30),
            List.of("git", "worktree", "list", "--porcelain")))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            root.resolve("data/projects/demo"),
            Duration.ofSeconds(30),
            List.of("git", "worktree", "repair")))
        .thenReturn(new Processes.Result(1, "", "boom"));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    try (FakeLogCapture log = new FakeLogCapture(DataFolderStart.class)) {
      new DataFolderStart(paths, processes, "").prepare();

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .contains("worktrees of a moved clone not repaired");
    }
  }
}
