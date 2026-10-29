package dev.jagt.orchestrator.job;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConflictFinishTest {

    private final CommandService commands = mock(CommandService.class);
    private final TaskStops stops = mock(TaskStops.class);

    @Test
    void pressesTheDeployTheHumanStarted(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());
        when(stops.moveDue("ABC-42")).thenReturn(true);

        new ConflictFinish(commands, stops, state).deploy("ABC-42", Path.of("/src/ABC-42-deploy"));

        verify(commands).execute("ABC-42", TaskAction.DEPLOY);
    }

    @Test
    void pressesNothingWhereTheTaskMadeTooManyUnattendedMovesInTheHour(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());
        when(stops.moveDue("ABC-42")).thenReturn(false);

        new ConflictFinish(commands, stops, state).deploy("ABC-42", Path.of("/src/ABC-42-deploy"));

        verify(commands, never()).execute("ABC-42", TaskAction.DEPLOY);
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void pressesNothingAndSaysOnceWhileJagtHoldsAResolvedConflictStopped(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT)
                .stop(new TaskStop(TaskStop.Trigger.UNCHANGED_FAILURE, 5, "same failure")).build());
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(ConflictFinish.class)).addAppender(log);
        ConflictFinish finish = new ConflictFinish(commands, stops, state);

        finish.deploy("ABC-42", Path.of("/src/ABC-42-deploy"));
        finish.deploy("ABC-42", Path.of("/src/ABC-42-deploy"));
        finish.deploy("ABC-42", Path.of("/src/ABC-42-deploy"));

        verify(commands, never()).execute("ABC-42", TaskAction.DEPLOY);
        assertThat(List.copyOf(log.list)).extracting(ILoggingEvent::getMessage)
                .containsExactly("conflict resolved held by stop");
    }

    @Test
    void finishesTheDeployOnTheTickAfterTheHumanLifts(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT)
                .stop(new TaskStop(TaskStop.Trigger.UNCHANGED_FAILURE, 5, "same failure")).build());
        when(stops.moveDue("ABC-42")).thenReturn(true);
        ConflictFinish finish = new ConflictFinish(commands, stops, state);
        finish.deploy("ABC-42", Path.of("/src/ABC-42-deploy"));

        state.updateTask("ABC-42", task -> task.lifted(9));
        finish.deploy("ABC-42", Path.of("/src/ABC-42-deploy"));

        verify(commands, times(1)).execute("ABC-42", TaskAction.DEPLOY);
    }

    private static StateService state(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }
}
