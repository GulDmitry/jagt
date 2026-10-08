package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeJobTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final IntakeCandidates candidates = mock(IntakeCandidates.class);
    private final IntakeStart starts = mock(IntakeStart.class);
    private final IntakeJob job = new IntakeJob(configService, candidates, starts);

    @Test
    void takesNothingOffTheTrackerWhileIntakeIsOff() {
        when(configService.load()).thenReturn(ConfigFile.defaults());

        job.run();

        verify(candidates, never()).waiting(any());
    }

    @Test
    void takesNothingOffTheTrackerWhileAStageNameIsStillBlank() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(TrackerConfig.defaults().withMode("both").withWorkflow("jira").withAssignee("jdoe")
                        .withStartStatus("In Progress")));

        job.run();

        verify(candidates, never()).waiting(any());
    }

    @Test
    void stopsThePollAtTheFirstItemThatCouldNotStart() {
        IntakeCandidates.Ready first = new IntakeCandidates.Ready(TicketFacts.defaults().withKey("ABC-1"),
                TokenUsage.NONE);
        IntakeCandidates.Ready second = new IntakeCandidates.Ready(TicketFacts.defaults().withKey("ABC-2"),
                TokenUsage.NONE);
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null)));
        when(candidates.waiting(any())).thenReturn(Optional.of(List.of(first, second)));
        when(starts.start(first)).thenReturn(false);

        job.run();

        verify(starts, never()).start(second);
    }
}
