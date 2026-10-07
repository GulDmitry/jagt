package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeJobTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final IntakeCandidates candidates = mock(IntakeCandidates.class);
    private final ProjectRouting routing = mock(ProjectRouting.class);
    private final IntakeHistory history = mock(IntakeHistory.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final IntakeJob job = new IntakeJob(configService, candidates, routing, history, launcher);

    @Test
    void takesNothingOffTheTrackerWhileIntakeIsOff() {
        when(configService.load()).thenReturn(ConfigFile.defaults());

        job.run();

        verify(candidates, never()).waiting(any());
    }

    @Test
    void takesNothingOffTheTrackerWhileAStageNameIsStillBlank() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(TrackerConfig.defaults().withMode("both").withWorkflow("jira").withAssignee("dzmitry")
                        .withStartStatus("In Progress")));

        job.run();

        verify(candidates, never()).waiting(any());
    }

    @Test
    void startsTheTaskForAnItemWhoseLabelsNameOneConfiguredProject() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTitle("Widget layout is off").withUrl("https://tracker/ABC-42")
                .withTrackerStatus("In Progress").withLabels(List.of("backend"));
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "dzmitry", "In Progress", "Ready for Stage", null))
                .withProjects(Map.of("api", new ProjectConfig("/api", "origin/main", "dev",
                        List.of("backend")))));
        when(candidates.waiting(any())).thenReturn(Optional.of(List.of(
                new IntakeCandidates.Ready(item, TokenUsage.NONE))));
        when(routing.projectFor(item)).thenReturn(new ProjectRouting.Placed("api"));
        when(launcher.launch(any(), any())).thenReturn(Launched.created("ABC-42", "started"));

        job.run();

        verify(launcher).launch(LaunchRequest.of("ABC-42").withProject("api"),
                new Answer<>(Optional.of(item), TokenUsage.NONE));
    }

    @Test
    void turnsAwayAnItemNothingCouldPlaceInAProject() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTitle("Widget layout is off").withUrl("https://tracker/ABC-42")
                .withTrackerStatus("In Progress").withLabels(List.of("nothing-matches-this"));
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "dzmitry", "In Progress", "Ready for Stage", null))
                .withProjects(Map.of("api", new ProjectConfig("/api", "origin/main", "dev",
                        List.of("backend")))));
        when(candidates.waiting(any())).thenReturn(Optional.of(List.of(
                new IntakeCandidates.Ready(item, TokenUsage.NONE))));
        when(routing.projectFor(item)).thenReturn(new ProjectRouting.Undecided("placed in no configured project"));

        job.run();

        verify(history).turnAway(eq("ABC-42"), anyString());
        verify(launcher, never()).launch(any(), any());
    }

    @Test
    void stopsThePollAtTheFirstLaunchTheBoardRefused() {
        TicketFacts first = TicketFacts.defaults().withExists(true).withKey("ABC-1").withTitle("One")
                .withUrl("https://tracker/ABC-1").withTrackerStatus("In Progress")
                .withLabels(List.of("backend"));
        TicketFacts second = TicketFacts.defaults().withExists(true).withKey("ABC-2").withTitle("Two")
                .withUrl("https://tracker/ABC-2").withTrackerStatus("In Progress")
                .withLabels(List.of("backend"));
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "dzmitry", "In Progress", "Ready for Stage", null))
                .withProjects(Map.of("api", new ProjectConfig("/api", "origin/main", "dev",
                        List.of("backend")))));
        when(candidates.waiting(any())).thenReturn(Optional.of(List.of(
                new IntakeCandidates.Ready(first, TokenUsage.NONE),
                new IntakeCandidates.Ready(second, TokenUsage.NONE))));
        when(routing.projectFor(first)).thenReturn(new ProjectRouting.Placed("api"));
        when(routing.projectFor(second)).thenReturn(new ProjectRouting.Placed("api"));
        when(launcher.launch(any(), any()))
                .thenThrow(new IllegalArgumentException("24 tasks are already open, which is the limit"));

        job.run();

        verify(launcher).launch(LaunchRequest.of("ABC-1").withProject("api"),
                new Answer<>(Optional.of(first), TokenUsage.NONE));
        verify(launcher, never()).launch(LaunchRequest.of("ABC-2").withProject("api"),
                new Answer<>(Optional.of(second), TokenUsage.NONE));
    }

    @Test
    void leavesAnItemTheRouterCouldNotAnswerForTheNextPollInsteadOfTurningItAway() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTitle("Widget layout is off").withUrl("https://tracker/ABC-42")
                .withTrackerStatus("In Progress");
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "dzmitry", "In Progress", "Ready for Stage", null))
                .withProjects(Map.of("api", new ProjectConfig("/api", "origin/main", "dev", List.of()))));
        when(candidates.waiting(any())).thenReturn(Optional.of(List.of(
                new IntakeCandidates.Ready(item, TokenUsage.NONE))));
        when(routing.projectFor(item)).thenReturn(new ProjectRouting.Unreadable("the router answered nothing"));

        job.run();

        verify(history, never()).turnAway(anyString(), anyString());
        verify(launcher, never()).launch(any(), any());
    }
}
