package dev.jawo.orchestrator.service;

import dev.jawo.orchestrator.config.OrchestratorPaths;
import dev.jawo.orchestrator.config.OrchestratorProperties;
import dev.jawo.orchestrator.model.TaskState;
import dev.jawo.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StateServiceTest {

    @Test
    void resolvesCallerTaskWhenCallerReportsPhysicalPathOfSymlinkedWorktree(@TempDir Path root) throws IOException {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, false, null)));
        state.putTask("ABC-1", new TaskState("proj", root.toString(), TaskStatus.NEW, 0, null, "a1", null));
        String physicalCallerCwd = root.toRealPath().toString();

        var found = state.findByWorktree(physicalCallerCwd);

        assertThat(found).map(Map.Entry::getKey).contains("ABC-1");
    }

    @Test
    void forgetsTaskWhenItIsRemoved(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, false, null)));
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.DONE, 0, null, "a1", null));

        boolean removed = state.removeTask("ABC-1");

        assertThat(removed).isTrue();
        assertThat(state.task("ABC-1")).isEmpty();
    }
}
