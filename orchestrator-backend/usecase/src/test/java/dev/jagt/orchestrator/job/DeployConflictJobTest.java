package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.service.ConfigService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeployConflictJobTest {

    private final ConfigService config = mock(ConfigService.class);
    private final DeployConflicts conflicts = mock(DeployConflicts.class);
    private final ConflictHandOff handOff = mock(ConflictHandOff.class);

    @Test
    void handsEachWaitingConflictOnWhereTheMasterActs() {
        DeployConflicts.WaitingConflict conflict = new DeployConflicts.WaitingConflict(Path.of("/src/ABC-42-deploy"), false);
        when(config.load()).thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));
        when(conflicts.waiting()).thenReturn(Map.of("ABC-42", conflict));

        new DeployConflictJob(config, conflicts, handOff).run();

        verify(handOff).handle("ABC-42", conflict);
    }

    @Test
    void leavesTheConflictToTheHumanWhereTheMasterOnlyJudges() {
        when(config.load()).thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("judge")));

        new DeployConflictJob(config, conflicts, handOff).run();

        verifyNoInteractions(conflicts, handOff);
    }
}
