package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketCloseJobTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final TrackerClose close = mock(TrackerClose.class);
    private final CommandService commands = mock(CommandService.class);

    @Test
    void asksAboutNothingWhileIntakeIsOff(@TempDir Path root) {
        StateService stateService = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigFile.defaults());
        TicketCloseJob job = new TicketCloseJob(configService, stateService, close, commands);

        job.run();

        verify(close, never()).closes(anyString(), any());
    }

    @Test
    void asksAboutNoTaskWhoseWorkIsStillInItsWorktree(@TempDir Path root) {
        StateService stateService = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        stateService.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        TicketCloseJob job = new TicketCloseJob(configService, stateService, close, commands);

        job.run();

        verify(close, never()).closes(anyString(), any());
    }

    @Test
    void closesTheTaskTheTrackerSaysIsFinishedWith(@TempDir Path root) {
        StateService stateService = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        stateService.putTask("ABC-42", task);
        when(close.closes("ABC-42", task)).thenReturn(true);
        TicketCloseJob job = new TicketCloseJob(configService, stateService, close, commands);

        job.run();

        verify(commands).execute("ABC-42", TaskAction.DONE);
    }

    @Test
    void reachesTheTaskPastTheReadsOnePollCanAffordOnTheNextPoll(@TempDir Path root) {
        StateService stateService = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        stateService.putTask("ABC-1", task);
        stateService.putTask("ABC-2", task);
        stateService.putTask("ABC-3", task);
        stateService.putTask("ABC-4", task);
        stateService.putTask("ABC-5", task);
        stateService.putTask("ABC-6", task);
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        TicketCloseJob job = new TicketCloseJob(configService, stateService, close, commands);

        job.run();
        job.run();

        verify(close).closes("ABC-6", task);
    }

    @Test
    void buysNoSecondReadAboutATaskThatHasNotMovedSinceItWasAsked(@TempDir Path root) {
        StateService stateService = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1_000L, null))).build();
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        stateService.putTask("ABC-42", task);
        when(close.closes("ABC-42", task)).thenReturn(false);
        TicketCloseJob job = new TicketCloseJob(configService, stateService, close, commands);

        job.run();
        job.run();

        verify(close, times(1)).closes("ABC-42", task);
    }

    @Test
    void asksAgainTheMomentTheTaskItselfHasMoved(@TempDir Path root) {
        StateService stateService = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState stood = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1_000L, null))).build();
        TaskState moved = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 2_000L, null))).build();
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        stateService.putTask("ABC-42", stood);
        when(close.closes(anyString(), any())).thenReturn(false);
        TicketCloseJob job = new TicketCloseJob(configService, stateService, close, commands);
        job.run();
        stateService.putTask("ABC-42", moved);

        job.run();

        verify(close).closes("ABC-42", moved);
    }

    @Test
    void leavesOpenTheTaskTheTrackerHasNotFinishedWith(@TempDir Path root) {
        StateService stateService = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        stateService.putTask("ABC-42", task);
        when(close.closes("ABC-42", task)).thenReturn(false);
        TicketCloseJob job = new TicketCloseJob(configService, stateService, close, commands);

        job.run();

        verify(commands, never()).execute(anyString(), any());
    }
}
