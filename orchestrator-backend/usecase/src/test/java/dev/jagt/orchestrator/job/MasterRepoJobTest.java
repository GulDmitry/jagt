package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
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
                .message("needs the registry").report(AgentReport.QUESTION).repoRequest("registry").build()));

        new MasterRepoJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press).execute("ABC-1", TaskAction.ADD_REPO);
    }

    @Test
    void leavesTheRepositoryToTheHumanWhoKeptIt() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("repo"), null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("needs the registry").report(AgentReport.QUESTION).repoRequest("registry").build()));

        new MasterRepoJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press, never()).execute(anyString(), any());
    }

    @Test
    void attachesNothingWhereTheMasterOnlyJudges() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("needs the registry").report(AgentReport.QUESTION).repoRequest("registry").build()));

        new MasterRepoJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press, never()).execute(anyString(), any());
    }

    @Test
    void attachesNothingForATaskThatAskedForNoRepository() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(state.tasks()).thenReturn(Map.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("which rule?").report(AgentReport.QUESTION).build()));

        new MasterRepoJob(state, config, press, new TaskTurns(state, Clock.systemUTC())).run();

        verify(press, never()).execute(anyString(), any());
    }

    @Test
    void pressesOnceWhileTheSameRequestStandsThoughJagtRestarts(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        new StateService(new JsonMapper(), paths).putTask("ABC-1", TaskState.builder("demo", "/wt",
                TaskStatus.IN_PROGRESS).message("needs the registry").report(AgentReport.QUESTION)
                .repoRequest("registry").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        StateService before = new StateService(new JsonMapper(), paths);
        new MasterRepoJob(before, config, press, new TaskTurns(before, Clock.systemUTC())).run();
        StateService restarted = new StateService(new JsonMapper(), paths);

        new MasterRepoJob(restarted, config, press, new TaskTurns(restarted, Clock.systemUTC())).run();

        verify(press, times(1)).execute("ABC-1", TaskAction.ADD_REPO);
    }

    @Test
    void pressesAgainWhenTheSameProjectIsAskedForAfterTheFirstRequestEnded(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService tasks = new StateService(new JsonMapper(), paths);
        tasks.putTask("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
                .message("needs the registry").report(AgentReport.QUESTION).repoRequest("registry").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        MasterRepoJob job = new MasterRepoJob(tasks, config, press, new TaskTurns(tasks, Clock.systemUTC()));

        job.run();
        tasks.updateTask("ABC-1", task -> task.withRepoRequest(null));
        job.run();
        tasks.updateTask("ABC-1", task -> task.withRepoRequest("registry"));
        job.run();

        verify(press, times(2)).execute("ABC-1", TaskAction.ADD_REPO);
    }
}
