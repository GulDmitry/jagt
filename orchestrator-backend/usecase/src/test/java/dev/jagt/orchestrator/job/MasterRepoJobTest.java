package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterRepoJobTest {

    private final StateService state = mock(StateService.class);
    private final ConfigService config = mock(ConfigService.class);
    private final MasterPress press = mock(MasterPress.class);

    @Test
    void attachesTheRepositoryASessionAskedForWhereTheMasterHoldsTheRight() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: needs the registry").repoRequest("registry").build()));

        new MasterRepoJob(state, config, press).run();

        verify(press).execute("ABC-1", TaskAction.ADD_REPO);
    }

    @Test
    void leavesTheRepositoryToTheHumanWhoKeptIt() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("repo"), null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: needs the registry").repoRequest("registry").build()));

        new MasterRepoJob(state, config, press).run();

        verify(press, never()).execute(anyString(), any());
    }

    @Test
    void attachesNothingWhereTheMasterOnlyJudges() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: needs the registry").repoRequest("registry").build()));

        new MasterRepoJob(state, config, press).run();

        verify(press, never()).execute(anyString(), any());
    }

    @Test
    void attachesNothingForATaskThatAskedForNoRepository() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: which rule?").build()));

        new MasterRepoJob(state, config, press).run();

        verify(press, never()).execute(anyString(), any());
    }

    @Test
    void pressesOnceWhileTheSameRequestStands() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: needs the registry").repoRequest("registry").build()));
        MasterRepoJob job = new MasterRepoJob(state, config, press);

        job.run();
        job.run();

        verify(press, times(1)).execute("ABC-1", TaskAction.ADD_REPO);
    }

    @Test
    void pressesAgainWhenTheSameProjectIsAskedForAfterTheFirstRequestEnded() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        TaskState asking = TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("awaiting: needs the registry").repoRequest("registry").build();
        TaskState working = TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS).message("working").build();
        when(state.tasks()).thenReturn(Map.of("ABC-1", asking), Map.of("ABC-1", working), Map.of("ABC-1", asking));
        MasterRepoJob job = new MasterRepoJob(state, config, press);

        job.run();
        job.run();
        job.run();

        verify(press, times(2)).execute("ABC-1", TaskAction.ADD_REPO);
    }
}
