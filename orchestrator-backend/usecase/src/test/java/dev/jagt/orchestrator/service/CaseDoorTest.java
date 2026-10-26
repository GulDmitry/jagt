package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Turn;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseDoorTest {

    private static final String SHIP_CASE =
            "## ship\n\nmove: ship\nat: REVIEW_PENDING\nby: master\nThe Master ships it.\n";

    private static CaseDoor doorOver(OrchestratorPaths paths, StateService state) {
        return new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                        new Notifications(List.of(), Runnable::run), Clock.systemUTC()));
    }

    @Test
    void letsAnyPressThroughThatTheMasterDidNotMake(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        CaseDoor door = doorOver(paths, state);

        assertThat(door.presses("ABC-42", TaskAction.DONE)).isTrue();
        assertThat(door.presses("ABC-42", "ship")).isTrue();
        assertThat(CaseDoor.byTheMaster()).isFalse();
    }

    @Test
    void refusesTheMasterAVerbOnlyAHumanPresses(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
        CaseDoor door = doorOver(paths, state);

        assertThatThrownBy(() -> OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", TaskAction.DONE)))
                .isInstanceOfSatisfying(Refusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
    }

    @Test
    void letsTheMasterPressAVerbACaseAllowsAtTheTasksStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("master-cases.md"), SHIP_CASE);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
        CaseDoor door = doorOver(paths, state);

        boolean byVerb = OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", TaskAction.SHIP));
        boolean byMove = OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", "ship"));

        assertThat(byVerb).isTrue();
        assertThat(byMove).isTrue();
        assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
    }

    @Test
    void stopsTheTaskWhereNoMasterCaseAllowsTheVerbAtItsStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("master-cases.md"), SHIP_CASE);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        CaseDoor door = doorOver(paths, state);

        boolean byVerb = OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", TaskAction.SHIP));

        assertThat(byVerb).isFalse();
        assertThat(state.task("ABC-42").orElseThrow().stop()).extracting(TaskStop::trigger, TaskStop::evidence)
                .containsExactly(TaskStop.Trigger.MISS, "no master case allows ship at IN_PROGRESS");
    }

    @Test
    void refusesTheMasterAMoveNoCaseAllowsAtTheTasksStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("master-cases.md"), SHIP_CASE);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        CaseDoor door = doorOver(paths, state);

        boolean byMove = OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", "ship"));

        assertThat(byMove).isFalse();
    }

    @Test
    void takesNoChangesFromAShipTheMasterPressedOnACleanPushedTree(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .turn(Turn.none().relayed("ship", "d1", 100)).build());
        CaseDoor door = doorOver(paths, state);

        boolean taken = OriginContext.as(ActionOrigin.MASTER,
                () -> door.takes("ABC-42", TaskStatus.CI_POLLING, AgentReport.NO_CHANGES));

        assertThat(taken).isTrue();
        assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
    }

    @Test
    void knowsTheMasterByTheOriginTheCallRunsUnder() {
        assertThat(OriginContext.as(ActionOrigin.MASTER, CaseDoor::byTheMaster)).isTrue();
        assertThat(OriginContext.as(ActionOrigin.BOARD, CaseDoor::byTheMaster)).isFalse();
    }
}
