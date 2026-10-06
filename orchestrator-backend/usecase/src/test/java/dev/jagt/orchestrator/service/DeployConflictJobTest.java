package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.capability.deploy.DeployService;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeployConflictJobTest {

    private final ConfigService config = mock(ConfigService.class);
    private final DeployService deploys = mock(DeployService.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final CommandService commands = mock(CommandService.class);
    private final DeployConflictJob job = new DeployConflictJob(config, deploys, sessions, commands);

    @Test
    void handsAConflictToTheSessionWhereTheMasterActs() {
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withMaster(new MasterConfig("act", null, null, List.of("deploy", "revert"), null)));
        when(deploys.conflicts()).thenReturn(Map.of("ABC-42",
                new DeployService.WaitingConflict(Path.of("/src/ABC-42-deploy"), false)));

        job.run();

        verify(sessions).relayIfChanged(eq("ABC-42"), contains("/src/ABC-42-deploy"));
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void asksOncePerConflictEvenWhereAnotherRelayOverwroteTheAsk() {
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withMaster(new MasterConfig("act", null, null, null, null)));
        when(deploys.conflicts()).thenReturn(Map.of("ABC-42",
                new DeployService.WaitingConflict(Path.of("/src/ABC-42-deploy"), false)));

        job.run();
        job.run();

        verify(sessions, times(1)).relayIfChanged(eq("ABC-42"), anyString());
    }

    @Test
    void finishesTheDeployOnceTheResolutionIsStagedInFull() {
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withMaster(new MasterConfig("act", null, null, List.of("deploy", "revert"), null)));
        when(deploys.conflicts()).thenReturn(Map.of("ABC-42",
                new DeployService.WaitingConflict(Path.of("/src/ABC-42-deploy"), true)));

        job.run();

        verify(commands).execute("ABC-42", TaskAction.DEPLOY);
    }

    @Test
    void leavesTheConflictToTheHumanWhereTheMasterOnlyJudges() {
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withMaster(new MasterConfig("judge", null, null, null, null)));

        job.run();

        verifyNoInteractions(deploys, sessions, commands);
    }
}
