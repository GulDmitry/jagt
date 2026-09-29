package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.IntakeConfig;
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
    private final IntakeRouting routing = mock(IntakeRouting.class);
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
                .withIntake(new IntakeConfig(true, "jira", "dzmitry", "In Progress", null, null)));

        job.run();

        verify(candidates, never()).waiting(any());
    }

    @Test
    void startsTheTaskForAnItemWhoseLabelsNameOneConfiguredProject() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTitle("Widget layout is off").withUrl("https://tracker/ABC-42")
                .withTrackerStatus("In Progress").withLabels(List.of("backend"));
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withIntake(new IntakeConfig(true, "jira", "dzmitry", "In Progress", "Ready for Stage", null))
                .withProjects(Map.of("api", new ProjectConfig("/api", "origin/main", "dev",
                        List.of("backend")))));
        when(candidates.waiting(any())).thenReturn(Optional.of(List.of(
                new IntakeCandidates.Ready(item, TokenUsage.NONE))));
        when(routing.projectFor(item)).thenReturn(Optional.of("api"));
        when(launcher.launch(any(), any())).thenReturn(Launched.created("ABC-42", "started"));

        job.run();

        verify(launcher).launch(new LaunchRequest("ABC-42", "api", null, null, null, null),
                new Answer<>(Optional.of(item), TokenUsage.NONE));
    }

    @Test
    void turnsAwayAnItemNothingCouldPlaceInAProject() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTitle("Widget layout is off").withUrl("https://tracker/ABC-42")
                .withTrackerStatus("In Progress").withLabels(List.of("nothing-matches-this"));
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withIntake(new IntakeConfig(true, "jira", "dzmitry", "In Progress", "Ready for Stage", null))
                .withProjects(Map.of("api", new ProjectConfig("/api", "origin/main", "dev",
                        List.of("backend")))));
        when(candidates.waiting(any())).thenReturn(Optional.of(List.of(
                new IntakeCandidates.Ready(item, TokenUsage.NONE))));
        when(routing.projectFor(item)).thenReturn(Optional.empty());

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
                .withIntake(new IntakeConfig(true, "jira", "dzmitry", "In Progress", "Ready for Stage", null))
                .withProjects(Map.of("api", new ProjectConfig("/api", "origin/main", "dev",
                        List.of("backend")))));
        when(candidates.waiting(any())).thenReturn(Optional.of(List.of(
                new IntakeCandidates.Ready(first, TokenUsage.NONE),
                new IntakeCandidates.Ready(second, TokenUsage.NONE))));
        when(routing.projectFor(first)).thenReturn(Optional.of("api"));
        when(routing.projectFor(second)).thenReturn(Optional.of("api"));
        when(launcher.launch(any(), any()))
                .thenThrow(new IllegalArgumentException("24 tasks are already open, which is the limit"));

        job.run();

        verify(launcher).launch(new LaunchRequest("ABC-1", "api", null, null, null, null),
                new Answer<>(Optional.of(first), TokenUsage.NONE));
        verify(launcher, never()).launch(new LaunchRequest("ABC-2", "api", null, null, null, null),
                new Answer<>(Optional.of(second), TokenUsage.NONE));
    }
}
