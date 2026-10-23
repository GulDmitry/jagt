package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

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
    void pressesTheVerbWhereTheTaskTouchedNoPathKeptForTheHuman() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.empty());

        boolean pressed = new MasterPress(commands, humanPaths, config).press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(pressed).isTrue();
        verify(commands).execute("ABC-1", TaskAction.DEPLOY);
    }

    @Test
    void leavesTheVerbToTheHumanWhereTheTaskTouchedAPathKeptForThem() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

        boolean pressed = new MasterPress(commands, humanPaths, config).press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(pressed).isFalse();
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void countsAPressAsAnUnattendedStart() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.empty());

        new MasterPress(commands, humanPaths, config).press("ABC-1", task, TaskAction.DEPLOY);

        verify(config).startTaken();
    }

    @Test
    void countsNothingWhereTheVerbWasLeftToTheHuman() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

        new MasterPress(commands, humanPaths, config).press("ABC-1", task, TaskAction.DEPLOY);

        verify(config, never()).startTaken();
    }

    @Test
    void pressesAVerbNoPathCanKeepWithoutAskingWhatTheTaskTouched() {
        new MasterPress(commands, humanPaths, config).execute("ABC-1", TaskAction.ADD_REPO);

        verify(commands).execute("ABC-1", TaskAction.ADD_REPO);
        verifyNoInteractions(humanPaths);
    }
}
