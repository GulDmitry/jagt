package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineProjectTest {

    private final TaskState twoHosts = TaskState.builder(List.of(
            new TaskRepo("api", "/wt/api", null, "https://git.example.invalid/acme/api/-/merge_requests/4"),
            new TaskRepo("web", "/wt/web", null, "https://ghe.example.invalid/acme/web/pull/9")),
            TaskStatus.REVIEW_PENDING).build();

    @Test
    void thePipelineBelongsToTheProjectWhoseRequestLivesOnItsHost() {
        assertThat(PipelineProject.of(twoHosts, "https://ghe.example.invalid/acme/web/actions/runs/5")).isEqualTo("web");
    }

    @Test
    void aPipelineOnNoKnownHostBelongsToTheTasksFirstProject() {
        assertThat(PipelineProject.of(twoHosts, "https://elsewhere.example.invalid/acme/x/actions/runs/5")).isEqualTo("api");
    }
}
