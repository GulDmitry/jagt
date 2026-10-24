package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterDeployJobTest {

    private final StateService state = mock(StateService.class);
    private final ConfigService config = mock(ConfigService.class);
    private final MasterPress press = mock(MasterPress.class);

    @Test
    void deploysAReviewedTaskWhereDeployIsNotKeptByTheHuman() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("revert"), null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build()));

        new MasterDeployJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press).press(eq("ABC-1"), any(), eq(TaskAction.DEPLOY));
    }

    @Test
    void deploysATaskApprovedOnTheHostBeforeTheJobLookedAtIt() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.APPROVED).build()));

        new MasterDeployJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press).press(eq("ABC-1"), any(), eq(TaskAction.DEPLOY));
    }

    @Test
    void leavesTheDeployToTheHumanWhoKeptIt() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("deploy"), null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build()));

        new MasterDeployJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press, never()).press(anyString(), any(), any());
    }

    @Test
    void deploysNothingWhoseRequestIsStillOpenToReview() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.CI_POLLING).build()));

        new MasterDeployJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press, never()).press(anyString(), any(), any());
    }

    @Test
    void pressesDeployOnceForAnArrivalThoughJagtRestarts(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        new StateService(new JsonMapper(), paths).putTask("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        StateService before = new StateService(new JsonMapper(), paths);
        new MasterDeployJob(before, config, press, new TaskTurns(before, Clock.systemUTC())).run();
        StateService restarted = new StateService(new JsonMapper(), paths);

        new MasterDeployJob(restarted, config, press, new TaskTurns(restarted, Clock.systemUTC())).run();

        verify(press, times(1)).press(eq("ABC-1"), any(), eq(TaskAction.DEPLOY));
    }

    @Test
    void deploysNothingWhereTheMasterOnlyJudges() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build()));

        new MasterDeployJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press, never()).press(anyString(), any(), any());
    }

    @Test
    void revertsNothingOnItsOwnOfADeployedTaskWhereTheHumanKeptTheRevert() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("revert"), null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("demo", "/wt", TaskStatus.DEPLOYED).build()));

        new MasterDeployJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press, never()).press(anyString(), any(), any());
    }
}
