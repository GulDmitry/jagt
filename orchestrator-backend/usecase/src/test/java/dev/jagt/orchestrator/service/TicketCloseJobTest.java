package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.IntakeConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketCloseJobTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final StateService stateService = mock(StateService.class);
    private final TicketReader tickets = mock(TicketReader.class);
    private final TrackerWorkflow workflow = mock(TrackerWorkflow.class);
    private final CommandService commands = mock(CommandService.class);
    private final TicketCloseJob job = new TicketCloseJob(configService, stateService, tickets, workflow,
            commands);

    @Test
    void closesNothingWhileIntakeIsOff() {
        when(configService.load()).thenReturn(ConfigFile.defaults());

        job.run();

        verify(tickets, never()).read(anyString());
    }

    @Test
    void buysNoReadAboutATaskWhoseWorkIsStillInItsWorktree() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withIntake(new IntakeConfig(true, "jira", "dzmitry", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-42",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()));

        job.run();

        verify(tickets, never()).read(anyString());
    }

    @Test
    void closesTheTaskWhoseItemReachedTheStageThatSaysTheWorkLanded() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withIntake(new IntakeConfig(true, "jira", "dzmitry", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-42",
                TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build()));
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget")
                .withUrl("https://tracker/ABC-42").withTrackerStatus("Ready for Stage");
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(workflow.closesWork(item)).thenReturn(true);

        job.run();

        verify(commands).execute("ABC-42", TaskAction.DONE);
    }

    @Test
    void leavesTheTaskOpenWhileItsItemStandsShortOfThatStage() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withIntake(new IntakeConfig(true, "jira", "dzmitry", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-42",
                TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build()));
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget")
                .withUrl("https://tracker/ABC-42").withTrackerStatus("In Review");
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(workflow.closesWork(item)).thenReturn(false);

        job.run();

        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void leavesTheTaskOpenWhenNobodyCouldReadTheStageItStandsIn() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withIntake(new IntakeConfig(true, "jira", "dzmitry", "In Progress", "Ready for Stage", null)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-42",
                TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build()));
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));

        job.run();

        verify(commands, never()).execute(anyString(), any());
        verify(workflow, never()).closesWork(any());
    }
}
