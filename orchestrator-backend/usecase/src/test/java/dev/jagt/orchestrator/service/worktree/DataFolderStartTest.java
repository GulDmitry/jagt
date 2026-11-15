package dev.jagt.orchestrator.service.worktree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class DataFolderStartTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  @Test
  void createsTheDataFolderOnFirstStart() {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.resolve("vault").toString());

    new DataFolderStart(new OrchestratorPaths(properties)).prepare();

    assertThat(root.resolve("vault")).isDirectory();
  }

  @Test
  void movesTheStateOfOlderVersionIntoTheDataFolderWithItsTasksAndRecordedWorktrees()
      throws Exception {
    Path recorded = root.resolve("beside-the-repository/ABC-1-demo");
    OrchestratorProperties older = OrchestratorProperties.defaults().withRoot(root.toString());
    new StateService(new JsonMapper(), new OrchestratorPaths(older), reducer, event -> {})
        .putTask(
            "ABC-1",
            TaskState.builder("demo", recorded.toString(), TaskStatus.IN_PROGRESS).build());
    Files.writeString(root.resolve("state.json.bak"), "{}");
    OrchestratorPaths paths =
        new OrchestratorPaths(older.withData(root.resolve("vault").toString()));

    new DataFolderStart(paths).prepare();

    StateService resumed = new StateService(new JsonMapper(), paths, reducer, event -> {});
    assertThat(resumed.task("ABC-1"))
        .get()
        .extracting(TaskState::worktreePath)
        .isEqualTo(recorded.toString());
    assertThat(root.resolve("state.json")).doesNotExist();
    assertThat(root.resolve("vault/state.json.bak")).exists();
  }

  @Test
  void movesNothingWhenTheDataFolderHoldsStateAlready() throws Exception {
    Files.writeString(root.resolve("state.json"), "old");
    Files.createDirectories(root.resolve("vault"));
    Files.writeString(root.resolve("vault/state.json"), "current");
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.resolve("vault").toString());

    new DataFolderStart(new OrchestratorPaths(properties)).prepare();

    assertThat(root.resolve("vault/state.json")).hasContent("current");
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

    new DataFolderStart(new OrchestratorPaths(properties)).prepare();

    assertThat(root.resolve("state.json")).hasContent("old");
  }
}
