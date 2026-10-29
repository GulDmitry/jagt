package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MasterPressTest {

    private final CommandService commands = mock(CommandService.class);
    private final HumanPaths humanPaths = mock(HumanPaths.class);
    private final TaskStops stops = mock(TaskStops.class);

    @Test
    void pressesTheVerbWhereTheTaskTouchedNoPathKeptForTheHuman(@TempDir Path root) {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(stops.moveDue("ABC-1")).thenReturn(true);
        when(humanPaths.touched(task)).thenReturn(Optional.empty());

        boolean pressed = new MasterPress(commands, humanPaths, stops, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(pressed).isTrue();
        verify(commands).execute("ABC-1", TaskAction.DEPLOY);
    }

    @Test
    void leavesTheVerbToTheHumanWhereTheTaskTouchedAPathKeptForThem(@TempDir Path root) {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

        boolean pressed = new MasterPress(commands, humanPaths, stops, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(pressed).isFalse();
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void refusesAPressWhereTheTaskMadeTooManyUnattendedMovesInTheHour(@TempDir Path root) {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(humanPaths.touched(task)).thenReturn(Optional.empty());
        when(stops.moveDue("ABC-1")).thenReturn(false);

        boolean pressed = new MasterPress(commands, humanPaths, stops, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(pressed).isFalse();
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void pressesAVerbNoPathCanKeepWithoutAskingWhatTheTaskTouched(@TempDir Path root) {
        when(stops.moveDue("ABC-1")).thenReturn(true);
        new MasterPress(commands, humanPaths, stops, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).execute("ABC-1", TaskAction.ADD_REPO);

        verify(commands).execute("ABC-1", TaskAction.ADD_REPO);
        verifyNoInteractions(humanPaths);
    }

    @Test
    void marksTheStepLeftToTheHumanWithThePathThatKeptItFromTheMaster(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        state.putTask("ABC-1", task);
        when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

        new MasterPress(commands, humanPaths, stops, new TaskTurns(state, Clock.systemUTC()))
                .press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(state.task("ABC-1").orElseThrow().turnOrNone().mark(TaskTurns.LEFT))
                .isEqualTo("deploy demo/src/auth/Login.java");
    }

    @Test
    void replacesTheStepLeftWithTheOneLeftNow(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED)
                .turn(Turn.none().withMark(TaskTurns.LEFT, "deploy demo/src/old/Old.java")).build();
        state.putTask("ABC-1", task);
        when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

        new MasterPress(commands, humanPaths, stops, new TaskTurns(state, Clock.systemUTC()))
                .press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(state.task("ABC-1").orElseThrow().turnOrNone().mark(TaskTurns.LEFT))
                .isEqualTo("deploy demo/src/auth/Login.java");
    }

    @Test
    void forgetsTheStepLeftOnceTheVerbIsPressed(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED)
                .turn(Turn.none().withMark(TaskTurns.LEFT, "deploy demo/src/old/Old.java")).build();
        state.putTask("ABC-1", task);
        when(stops.moveDue("ABC-1")).thenReturn(true);
        when(humanPaths.touched(task)).thenReturn(Optional.empty());

        new MasterPress(commands, humanPaths, stops, new TaskTurns(state, Clock.systemUTC()))
                .press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(state.task("ABC-1").orElseThrow().turnOrNone().mark(TaskTurns.LEFT)).isNull();
    }

    @Test
    void takesARefusedDeployAsAnOutcomeNotAFault(@TempDir Path root) {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(stops.moveDue("ABC-1")).thenReturn(true);
        when(humanPaths.touched(task)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Nothing to deploy")).when(commands).execute("ABC-1", TaskAction.DEPLOY);

        boolean pressed = new MasterPress(commands, humanPaths, stops, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC())).press("ABC-1", task, TaskAction.DEPLOY);

        assertThat(pressed).isFalse();
    }

    @Test
    void letsAFaultOtherThanARefusalEscape(@TempDir Path root) {
        when(stops.moveDue("ABC-1")).thenReturn(true);
        doThrow(new UncheckedIOException(new IOException("disk gone"))).when(commands).execute("ABC-1", TaskAction.DEPLOY);
        MasterPress press = new MasterPress(commands, humanPaths, stops, new TaskTurns(new StateService(new JsonMapper(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))), Clock.systemUTC()));

        assertThatThrownBy(() -> press.execute("ABC-1", TaskAction.DEPLOY)).isInstanceOf(UncheckedIOException.class);
    }
}
