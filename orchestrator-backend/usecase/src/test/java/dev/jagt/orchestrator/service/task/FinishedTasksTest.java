package dev.jagt.orchestrator.service.task;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FinishedTasksTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  private FinishedTasks finished;
  private StateService state;

  @BeforeEach
  void recordsFinishedTasksUnderTheTempRoot() {
    JsonMapper mapper = new JsonMapper();
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    state = new StateService(mapper, paths, reducer, event -> {});
    finished = new FinishedTasks(mapper, state, paths, mock(MasterReview.class), Clock.systemUTC());
  }

  @Test
  void keepsRetiredTaskAfterTheStateEntryIsGone() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1").title("a thing").build());

    finished.record("ABC-1");
    state.removeTask("ABC-1");

    List<FinishedTask> all = finished.all();
    assertThat(all)
        .singleElement()
        .satisfies(task -> {
          assertThat(task.identity().id()).isEqualTo("ABC-1");
          assertThat(task.identity().title()).isEqualTo("a thing");
        });
  }

  @Test
  void skipsLineItCannotParseInsteadOfLosingTheWholeLog() throws Exception {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1").build());
    finished.record("ABC-1");
    Files.writeString(
        root.resolve("finished.jsonl"),
        Files.readString(root.resolve("finished.jsonl")) + "{ this is not json\n");

    assertThat(finished.all())
        .singleElement()
        .satisfies(task -> assertThat(task.identity().id()).isEqualTo("ABC-1"));
  }

  @Test
  void packsTheOlderHalfBesideItOnceTheLogPassesItsCeiling() throws Exception {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1").build());
    state.putTask(
        "ABC-2", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a2").build());
    state.putTask(
        "ABC-3", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a3").build());
    finished.record("ABC-1");
    finished.record("ABC-2");
    Files.writeString(
        root.resolve("finished.jsonl"),
        Files.readString(root.resolve("finished.jsonl")) + "#".repeat(10 * 1024 * 1024) + "\n");

    finished.record("ABC-3");

    assertThat(finished.all()).extracting(task -> task.identity().id()).containsExactly("ABC-3");
    assertThat(finished.archives())
        .singleElement()
        .satisfies(archive -> {
          try (var in = new GZIPInputStream(Files.newInputStream(archive))) {
            assertThat(new String(in.readAllBytes(), UTF_8))
                .contains("\"ABC-1\"", "\"ABC-2\"")
                .doesNotContain("\"ABC-3\"");
          }
        });
  }
}
