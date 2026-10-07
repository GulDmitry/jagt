package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterDeployJobTest {

    private final StateService state = mock(StateService.class);
    private final ConfigService config = mock(ConfigService.class);
    private final CommandService commands = mock(CommandService.class);

    @Test
    void deploysAReviewedTaskWhereDeployIsNotKeptByTheHuman() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("revert"), null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build()));

        new MasterDeployJob(state, config, commands).run();

        verify(commands).execute("ABC-1", TaskAction.DEPLOY);
    }

    @Test
    void leavesTheDeployToTheHumanWhoKeptIt() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("deploy"), null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build()));

        new MasterDeployJob(state, config, commands).run();

        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void deploysNothingWhoseRequestIsStillOpenToReview() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.CI_POLLING).build()));

        new MasterDeployJob(state, config, commands).run();

        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void pressesDeployOnceWhileTheTaskStaysReviewed() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build()));
        MasterDeployJob job = new MasterDeployJob(state, config, commands);

        job.run();
        job.run();

        verify(commands, times(1)).execute("ABC-1", TaskAction.DEPLOY);
    }
}
