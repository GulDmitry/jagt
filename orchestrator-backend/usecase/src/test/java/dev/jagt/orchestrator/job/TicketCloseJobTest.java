package dev.jagt.orchestrator.job;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketCloseJobTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final StateService stateService = mock(StateService.class);
    private final TrackerClose close = mock(TrackerClose.class);
    private final CommandService commands = mock(CommandService.class);
    private final TicketCloseJob job = new TicketCloseJob(configService, stateService, close, commands);

    @Test
    void asksAboutNothingWhileIntakeIsOff() {
        when(configService.load()).thenReturn(ConfigFile.defaults());

        job.run();

        verify(close, never()).closes(anyString(), any());
    }

    @Test
    void asksAboutNoTaskWhoseWorkIsStillInItsWorktree() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-42",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()));

        job.run();

        verify(close, never()).closes(anyString(), any());
    }

    @Test
    void closesTheTaskTheTrackerSaysIsFinishedWith() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-42", task));
        when(close.closes("ABC-42", task)).thenReturn(true);

        job.run();

        verify(commands).execute("ABC-42", TaskAction.DONE);
    }

    @Test
    void reachesTheTaskPastTheReadsOnePollCanAffordOnTheNextPoll() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        Map<String, TaskState> six = new LinkedHashMap<>();
        six.put("ABC-1", task);
        six.put("ABC-2", task);
        six.put("ABC-3", task);
        six.put("ABC-4", task);
        six.put("ABC-5", task);
        six.put("ABC-6", task);
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(six);

        job.run();
        job.run();

        verify(close).closes("ABC-6", task);
    }

    @Test
    void buysNoSecondReadAboutATaskThatHasNotMovedSinceItWasAsked() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1_000L, null))).build();
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-42", task));
        when(close.closes("ABC-42", task)).thenReturn(false);

        job.run();
        job.run();

        verify(close, times(1)).closes("ABC-42", task);
    }

    @Test
    void asksAgainTheMomentTheTaskItselfHasMoved() {
        TaskState stood = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1_000L, null))).build();
        TaskState moved = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 2_000L, null))).build();
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-42", stood), Map.of("ABC-42", moved));
        when(close.closes(anyString(), any())).thenReturn(false);

        job.run();
        job.run();

        verify(close).closes("ABC-42", moved);
    }

    @Test
    void leavesOpenTheTaskTheTrackerHasNotFinishedWith() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-42", task));
        when(close.closes("ABC-42", task)).thenReturn(false);

        job.run();

        verify(commands, never()).execute(anyString(), any());
    }
}
