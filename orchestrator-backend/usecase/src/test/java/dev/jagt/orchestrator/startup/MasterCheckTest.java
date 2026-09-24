package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterCheckTest {

    private final ConfigService configService = mock(ConfigService.class);

    private MasterCheck checking(Path root, MasterConfig master) {
        when(configService.load()).thenReturn(ConfigFile.defaults().withMaster(master));
        return new MasterCheck(configService, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())));
    }

    @Test
    void findsNothingWrongWithTheSettingEveryInstallShipsWith(@TempDir Path root) {
        assertThat(checking(root, MasterConfig.defaults()).problems()).isEmpty();
    }

    @Test
    void refusesAModeThisMachineDoesNotHave(@TempDir Path root) {
        assertThat(checking(root, new MasterConfig("supervise", null)).problems())
                .singleElement().asString().contains("orchestrator.master.mode");
    }

    @Test
    void refusesToRunAReviewerWithNoStandardsToJudgeBy(@TempDir Path root) {
        assertThat(checking(root, new MasterConfig("judge", null)).problems())
                .singleElement().asString().contains("orchestrator.master.brief: required");
    }

    @Test
    void refusesABriefThatIsNotThere(@TempDir Path root) {
        assertThat(checking(root, new MasterConfig("judge", "reviewer.md")).problems())
                .singleElement().asString().contains("no file at");
    }

    @Test
    void acceptsAModeWhoseBriefTheInstallActuallyCarries(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("reviewer.md"), "what I care about\n");

        assertThat(checking(root, new MasterConfig("act", "reviewer.md")).problems()).isEmpty();
    }
}
