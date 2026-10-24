package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MasterPressTest {

    private final CommandService commands = mock(CommandService.class);
    private final HumanPaths humanPaths = mock(HumanPaths.class);
    private final ConfigService config = mock(ConfigService.class);

    @Test
    void pressesTheVerbWhereTheTaskTouchedNoPathKeptForTheHuman(@TempDir Path root) {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.empty());

        boolean pressed = new MasterPress(commands, humanPaths, config, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(pressed).isTrue();
        verify(commands).execute("ABC-1", TaskAction.DEPLOY);
    }

    @Test
    void leavesTheVerbToTheHumanWhereTheTaskTouchedAPathKeptForThem(@TempDir Path root) {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

        boolean pressed = new MasterPress(commands, humanPaths, config, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(pressed).isFalse();
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void countsAPressAsAnUnattendedStart(@TempDir Path root) {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.empty());

        new MasterPress(commands, humanPaths, config, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).press("ABC-1", task, TaskAction.DEPLOY);

        verify(config).startTaken();
    }

    @Test
    void countsNothingWhereTheVerbWasLeftToTheHuman(@TempDir Path root) {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

        new MasterPress(commands, humanPaths, config, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).press("ABC-1", task, TaskAction.DEPLOY);

        verify(config, never()).startTaken();
    }

    @Test
    void pressesAVerbNoPathCanKeepWithoutAskingWhatTheTaskTouched(@TempDir Path root) {
        new MasterPress(commands, humanPaths, config, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).execute("ABC-1", TaskAction.ADD_REPO);

        verify(commands).execute("ABC-1", TaskAction.ADD_REPO);
        verifyNoInteractions(humanPaths);
    }
}
