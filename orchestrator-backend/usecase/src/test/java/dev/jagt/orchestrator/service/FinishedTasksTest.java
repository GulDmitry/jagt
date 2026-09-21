package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinishedTasksTest {

    @Test
    void keepsARetiredTaskAfterTheStateEntryIsGone(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(mapper, paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1")
                .title("a thing").build());
        FinishedTasks finished = new FinishedTasks(mapper, state, paths);

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
        FinishedTasks finished = new FinishedTasks(mapper, state, paths);
        finished.record("ABC-1");
        Files.writeString(root.resolve("finished.jsonl"),
                Files.readString(root.resolve("finished.jsonl")) + "{ this is not json\n");

        assertThat(finished.all()).singleElement()
                .satisfies(task -> assertThat(task.id()).isEqualTo("ABC-1"));
    }
}
