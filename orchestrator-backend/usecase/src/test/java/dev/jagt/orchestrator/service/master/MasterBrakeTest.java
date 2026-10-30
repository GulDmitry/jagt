package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MasterBrakeTest {

    @Test
    void stopsActWhenThreeTasksTheMasterMovedEndBadlyInARow(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);

        brake.observe(Map.of(
                "ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build(),
                "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.DEPLOY_CONFLICT).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.DEPLOY_CONFLICT, 21, ActionOrigin.MASTER))).build(),
                "ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.IN_PROGRESS, 12, ActionOrigin.MASTER)))
                        .silentSince(22).silentBecause("crash").build()));

        assertThat(root.resolve("act-stopped")).exists();
    }

    @Test
    void countsNoSilenceAfterTheSessionReportedItself(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);

        brake.observe(Map.of(
                "ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.IN_PROGRESS, 20, ActionOrigin.MCP)))
                        .silentSince(30).silentBecause("idle").build(),
                "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.IN_PROGRESS, 21, ActionOrigin.MCP)))
                        .silentSince(31).silentBecause("idle").build(),
                "ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.IN_PROGRESS, 22, ActionOrigin.MCP)))
                        .silentSince(32).silentBecause("idle").build()));

        assertThat(root.resolve("act-stopped")).doesNotExist();
    }

    @Test
    void stopsActWhenThreeSessionsFallSilentRightAfterTheMastersMove(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);

        brake.observe(Map.of(
                "ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.IN_PROGRESS, 10, ActionOrigin.MASTER)))
                        .silentSince(30).silentBecause("idle").build(),
                "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.IN_PROGRESS, 11, ActionOrigin.MASTER)))
                        .silentSince(31).silentBecause("idle").build(),
                "ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.IN_PROGRESS, 12, ActionOrigin.MASTER)))
                        .silentSince(32).silentBecause("idle").build()));

        assertThat(root.resolve("act-stopped")).exists();
    }

    @Test
    void countsNoSilenceAfterTheHumansOwnMove(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);

        brake.observe(Map.of(
                "ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.IN_PROGRESS, 20, ActionOrigin.BOARD)))
                        .silentSince(30).silentBecause("idle").build(),
                "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.IN_PROGRESS, 21, ActionOrigin.BOARD)))
                        .silentSince(31).silentBecause("idle").build(),
                "ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.IN_PROGRESS).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.IN_PROGRESS, 22, ActionOrigin.BOARD)))
                        .silentSince(32).silentBecause("idle").build()));

        assertThat(root.resolve("act-stopped")).doesNotExist();
    }

    @Test
    void keepsActWhereADeployCameBetweenTheBadEndings(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);

        brake.observe(Map.of(
                "ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build(),
                "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build(),
                "ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.DEPLOYED).history(List.of(
                        new StatusChange(TaskStatus.DEPLOYED, 22, ActionOrigin.MASTER))).build(),
                "ABC-4", TaskState.builder("proj", "/wt4", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 13, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.REVERTED, 23, ActionOrigin.BOARD))).build()));

        assertThat(root.resolve("act-stopped")).doesNotExist();
    }

    @Test
    void countsOneTaskEndingBadlyTwiceAsOne(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);

        brake.observe(Map.of(
                "ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.DEPLOY_CONFLICT, 20, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.REVERTED, 30, ActionOrigin.BOARD))).build(),
                "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build()));

        assertThat(root.resolve("act-stopped")).doesNotExist();
    }

    @Test
    void countsNoTaskTheMasterNeverMoved(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);

        brake.observe(Map.of(
                "ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build(),
                "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build(),
                "ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.REVERTED, 22, ActionOrigin.BOARD))).build()));

        assertThat(root.resolve("act-stopped")).doesNotExist();
    }

    @Test
    void countsNothingThatHadAlreadyEndedWhenJagtStarted(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build());
        MasterBrake brake = new MasterBrake(state, paths);
        brake.follow();

        state.putTask("ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 22, ActionOrigin.BOARD))).build());

        assertThat(root.resolve("act-stopped")).doesNotExist();
    }

    @Test
    void givesActBackWhenTheHumanRestoresIt(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);

        boolean restored = brake.restore();

        assertThat(restored).isTrue();
        assertThat(root.resolve("act-stopped")).doesNotExist();
    }

    @Test
    void stopsActOnEndingsWrittenToTheStateAfterItStartedFollowing(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        MasterBrake brake = new MasterBrake(state, paths);
        brake.follow();

        state.putTask("ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build());
        state.putTask("ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 22, ActionOrigin.BOARD))).build());

        assertThat(root.resolve("act-stopped")).exists();
    }

    @Test
    void startsTheCountAgainWhereTheHumanRestoredActBetweenBadEndings(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);
        brake.observe(Map.of("ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build(), "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build()));
        brake.restore();

        brake.observe(Map.of("ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build(), "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build(), "ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 22, ActionOrigin.BOARD))).build()));

        assertThat(root.resolve("act-stopped")).doesNotExist();
    }

    @Test
    void startsTheCountAgainOnceItHasStoppedAct(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);
        brake.observe(Map.of("ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build(), "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build(), "ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 22, ActionOrigin.BOARD))).build()));
        Files.delete(root.resolve("act-stopped"));

        brake.observe(Map.of("ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build(), "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build(), "ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 22, ActionOrigin.BOARD))).build(),
                "ABC-4", TaskState.builder("proj", "/wt4", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 13, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 23, ActionOrigin.BOARD))).build(), "ABC-5", TaskState.builder("proj", "/wt5", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 14, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 24, ActionOrigin.BOARD))).build()));

        assertThat(root.resolve("act-stopped")).doesNotExist();
    }

    @Test
    void keepsTheCountAcrossARestart(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        new MasterBrake(state, paths).observe(Map.of(
                "ABC-1", TaskState.builder("proj", "/wt1", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD))).build(),
                "ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVERTED).history(List.of(
                        new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD))).build()));
        MasterBrake restarted = new MasterBrake(state, paths);
        restarted.follow();

        restarted.observe(Map.of("ABC-3", TaskState.builder("proj", "/wt3", TaskStatus.REVERTED).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVERTED, 22, ActionOrigin.BOARD))).build()));

        assertThat(root.resolve("act-stopped")).exists();
    }

    @Test
    void saysActWasNotStoppedWhereThereWasNothingToRestore(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        MasterBrake brake = new MasterBrake(new StateService(new JsonMapper(), paths), paths);

        boolean restored = brake.restore();

        assertThat(restored).isFalse();
    }
}
