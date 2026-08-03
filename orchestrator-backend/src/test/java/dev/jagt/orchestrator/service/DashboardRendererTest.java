package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardRendererTest {

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false, null)));
    }

    @Test
    void showsTheTicketUrlAsADetailLineAboveTheMrLink(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.CI_POLLING, 0,
                null, "a1", null, "title", "https://gitlab/x/-/merge_requests/9", "https://jira/browse/ABC-1"));

        String out = new DashboardRenderer(state).render();

        assertThat(out).contains("└ https://jira/browse/ABC-1");
        assertThat(out.indexOf("https://jira/browse/ABC-1"))
                .isLessThan(out.indexOf("https://gitlab/x/-/merge_requests/9"));
    }

    @Test
    void omitsTheTicketLineWhenNoUrlWasRead(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.IN_PROGRESS, 0,
                null, "a1", null, "title", null, null));

        assertThat(new DashboardRenderer(state).render()).doesNotContain("└");
    }
}
