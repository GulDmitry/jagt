package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiftCapabilityTest {

    @Test
    void clearsTheStopAndTellsTheHumanWhatMovesAgain(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .stop(new TaskStop(TaskStop.Trigger.STALLED, 2, "3 rounds")).build());
        LiftCapability lift = new LiftCapability(new TaskStops(state,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run),
                Clock.systemUTC()));

        var outcome = lift.run("ABC-1");

        assertThat(lift.action()).isEqualTo(TaskAction.LIFT);
        assertThat(state.task("ABC-1").orElseThrow().stopped()).isFalse();
        assertThat(outcome.message()).isEqualTo(
                "lift ABC-1: auto-review and the Master may move it again; its rounds count anew");
    }
}
