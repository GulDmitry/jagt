package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FinishedTasksTest {

    @Test
    void keepsARetiredTaskAfterTheStateEntryIsGone(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(mapper, paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1")
                .title("a thing").build());
        FinishedTasks finished = new FinishedTasks(mapper, state, paths, mock(MasterReview.class));

        finished.record("ABC-1");
        state.removeTask("ABC-1");

        List<FinishedTask> all = finished.all();
        assertThat(all).singleElement().satisfies(task -> {
            assertThat(task.id()).isEqualTo("ABC-1");
            assertThat(task.title()).isEqualTo("a thing");
        });
    }

    @Test
    void skipsALineItCannotParseInsteadOfLosingTheWholeLog(@TempDir Path root) throws Exception {
        JsonMapper mapper = new JsonMapper();
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(mapper, paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1").build());
        FinishedTasks finished = new FinishedTasks(mapper, state, paths, mock(MasterReview.class));
        finished.record("ABC-1");
        Files.writeString(root.resolve("finished.jsonl"),
                Files.readString(root.resolve("finished.jsonl")) + "{ this is not json\n");

        assertThat(finished.all()).singleElement()
                .satisfies(task -> assertThat(task.id()).isEqualTo("ABC-1"));
    }

    @Test
    void packsTheOlderHalfBesideItOnceTheLogPassesItsCeiling(@TempDir Path root) throws Exception {
        JsonMapper mapper = new JsonMapper();
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(mapper, paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1").build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a2").build());
        state.putTask("ABC-3", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a3").build());
        state.putTask("ABC-4", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a4").build());
        FinishedTasks roomy = new FinishedTasks(mapper, state, paths, mock(MasterReview.class), 1_000_000);
        roomy.record("ABC-1");
        roomy.record("ABC-2");
        roomy.record("ABC-3");
        FinishedTasks full = new FinishedTasks(mapper, state, paths, mock(MasterReview.class), 1);

        full.record("ABC-4");

        assertThat(full.all()).extracting(FinishedTask::id).containsExactly("ABC-4", "ABC-3");
        assertThat(full.archives()).singleElement().satisfies(archive -> {
            try (var in = new GZIPInputStream(Files.newInputStream(archive))) {
                assertThat(new String(in.readAllBytes())).contains("\"ABC-1\"", "\"ABC-2\"")
                        .doesNotContain("\"ABC-3\"");
            }
        });
    }
}
