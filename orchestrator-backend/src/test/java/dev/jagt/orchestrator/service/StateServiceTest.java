package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StateServiceTest {

    @Test
    void loadsAStateFileWrittenBeforeTheAutoReviewFieldsExisted(@TempDir Path root) throws IOException {
        // A real state.json from before mrCreatedAt/lastPolledAt/autoReview were added: the new primitive
        // longs are simply absent. Jackson must default them to 0, not fail the whole load (which stranded
        // every task and left /state + the dashboard empty).
        Path stateFile = root.resolve("state.json");
        Files.writeString(stateFile, """
                {"tasks":{"ABC-1":{"project":"proj","worktreePath":"/wt","status":"CI_POLLING",
                "lastActiveTimestamp":123,"alias":"a1","mrUrl":"http://mr/1"}}}""");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(new OrchestratorProperties(
                root.toString(), null, stateFile.toString(),
                null, null, null, null, null, null, null, false, null)));

        var task = state.task("ABC-1").orElseThrow();

        assertThat(task.status()).isEqualTo(TaskStatus.CI_POLLING);
        assertThat(task.mrUrl()).isEqualTo("http://mr/1");
        assertThat(task.mrCreatedAt()).isZero();
        assertThat(task.lastPolledAt()).isZero();
        assertThat(task.autoReview()).isNull();
    }

    @Test
    void resolvesCallerTaskWhenCallerReportsPhysicalPathOfSymlinkedWorktree(@TempDir Path root) throws IOException {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false, null)));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.NEW).alias("a1").build());
        String physicalCallerCwd = root.toRealPath().toString();

        var found = state.findByWorktree(physicalCallerCwd);

        assertThat(found).map(Map.Entry::getKey).contains("ABC-1");
    }

    @Test
    void forgetsTaskWhenItIsRemoved(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false, null)));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1").build());

        boolean removed = state.removeTask("ABC-1");

        assertThat(removed).isTrue();
        assertThat(state.task("ABC-1")).isEmpty();
    }
}
