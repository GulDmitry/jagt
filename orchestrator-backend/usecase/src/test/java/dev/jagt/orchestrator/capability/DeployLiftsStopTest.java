package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DeployLiftsStopTest {

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC);

    @ParameterizedTest
    @EnumSource(value = ActionOrigin.class, names = {"BOARD", "PALETTE"})
    void deployedTaskIsCheckedAgainWhenAHumanDeployedItPastAStop(ActionOrigin human, @TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(mock(Notifications.class), new StopLog(new JsonMapper(), paths)), clock);
        stops.stop("ABC-42", TaskStop.Trigger.SHIPS, "ships without a new note");
        DeployLiftsStop lifter = new DeployLiftsStop(stops);

        OriginContext.as(human, () -> lifter.around("ABC-42", () -> {
            state.updateTask("ABC-42", task -> task.withStatus(TaskStatus.DEPLOYED, "deployed", 100_000_000));
            return Outcome.ok("deployed");
        }));

        assertThat(state.task("ABC-42").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
        assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ActionOrigin.class, names = {"MASTER", "JAGT", "MCP", "AUTO_REVIEW", "TRACKER"})
    void deployedTaskKeepsItsStopWhenNoHumanDeployedIt(ActionOrigin other, @TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(mock(Notifications.class), new StopLog(new JsonMapper(), paths)), clock);
        stops.stop("ABC-42", TaskStop.Trigger.SHIPS, "ships without a new note");
        DeployLiftsStop lifter = new DeployLiftsStop(stops);

        OriginContext.as(other, () -> lifter.around("ABC-42", () -> Outcome.ok("deployed")));

        assertThat(state.task("ABC-42").orElseThrow().stopped()).isTrue();
    }
}
