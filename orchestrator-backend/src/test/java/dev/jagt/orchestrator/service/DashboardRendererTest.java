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
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").title("title").mrUrl("https://gitlab/x/-/merge_requests/9").ticketUrl("https://jira/browse/ABC-1").build());

        String out = new DashboardRenderer(state).render();

        assertThat(out).contains("└ https://jira/browse/ABC-1");
        assertThat(out.indexOf("https://jira/browse/ABC-1"))
                .isLessThan(out.indexOf("https://gitlab/x/-/merge_requests/9"));
    }

    @Test
    void ordersRowsByLastActiveDescendingUnderTheSortedActiveColumn(@TempDir Path root) {
        StateService state = stateIn(root);
        long older = 1_700_000_000_000L;
        long newer = older + 3_600_000;
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW)
                .lastActiveTimestamp(older).alias("a1").title("older task").build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW)
                .lastActiveTimestamp(newer).alias("a2").title("newer task").build());

        String out = new DashboardRenderer(state).render();

        assertThat(out).contains("ACTIVE ▼");
        assertThat(out.indexOf("ABC-2")).isLessThan(out.indexOf("ABC-1"));
        assertThat(out).contains(DashboardRenderer.stamp(older), DashboardRenderer.stamp(newer));
    }

    @Test
    void omitsTheTicketLineWhenNoUrlWasRead(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .alias("a1").title("title").build());

        assertThat(new DashboardRenderer(state).render()).doesNotContain("└");
    }
}
