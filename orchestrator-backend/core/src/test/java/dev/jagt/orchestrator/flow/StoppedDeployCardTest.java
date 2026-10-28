package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoppedDeployCardTest {

    @Test
    void asksForTheLiftOfAStopThatOutlivedTheHumansDeploy() {
        TaskState deployed = TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .stop(new TaskStop(TaskStop.Trigger.MISS, 5, "no case")).build()
                .withStatus(TaskStatus.DEPLOYED, "deployed", true, 2_000);

        TaskView view = TaskView.of("ABC-42", deployed, false, AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(view.ask()).isEqualTo("lift the stop — no case for this step");
    }
}
