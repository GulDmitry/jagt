package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.ConflictWatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskReducerTest {

    @Test
    void forgetsTheDeployConflictOnceTheDeployLands() {
        TaskState conflicted = TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .conflict(ConflictWatch.handed(1_000, "/wt-deploy", new ConflictWatch.Progress(7, 252, "d"), 1_000))
                .build();

        TaskState deployed = TaskReducer.reduce(conflicted, new TaskMove.Ran(TaskAction.DEPLOY, Outcome.Kind.OK),
                "deployed", true, 2_000);

        assertThat(deployed.conflict()).isNull();
    }

    @Test
    void keepsTheDeployConflictWhereAHeldReportLeavesTheTaskInIt() {
        TaskState conflicted = TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .conflict(ConflictWatch.handed(1_000, "/wt-deploy", new ConflictWatch.Progress(7, 252, "d"), 1_000))
                .build();

        TaskState held = TaskReducer.reduce(conflicted, new TaskMove.Reported(TaskStatus.IN_PROGRESS, false),
                "working", false, 2_000);

        assertThat(held.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
        assertThat(held.conflict()).isEqualTo(conflicted.conflict());
    }
}
