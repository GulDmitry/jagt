package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterCaseCheckTest {

    private final ConfigService config = mock(ConfigService.class);

    @Test
    void passesTheShippedCasesAgainstTheMastersMoves(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));

        assertThat(new MasterCaseCheck(new MasterCases(paths), config, paths).problems()).isEmpty();
    }

    @Test
    void refusesACaseNamingAMoveTheMasterLacks(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        Files.writeString(root.resolve("master-cases.md"),
                "## revert\n\nmove: revert\nat: DEPLOYED\nby: master\nThe Master takes a deploy back.\n");
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));

        assertThat(new MasterCaseCheck(new MasterCases(paths), config, paths).problems()).containsExactly(
                new Problem("master-cases.md", "master-cases.md: case revert names move 'revert', which the Master lacks"));
    }

    @Test
    void refusesAMasterMoveNoCaseNames(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
        Files.writeString(root.resolve("master-cases.md.dist"),
                Files.readString(Path.of("../../master-cases.md.dist")).replace("move: answered-under", "move: answered"));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("judge")));

        assertThat(new MasterCaseCheck(new MasterCases(paths), config, paths).problems()).containsExactly(
                new Problem("master-cases.md", "master-cases.md: no case names the Master's move 'answered-under' — add"
                        + " one, or the Master never makes it"));
    }

    @Test
    void refusesAMasterCaseSetOffByAnyoneButTheMaster(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        Files.writeString(root.resolve("master-cases.md"),
                "## ship\n\nmove: ship\nat: REVIEW_PENDING\nby: human\nYou ship it.\n");
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));

        assertThat(new MasterCaseCheck(new MasterCases(paths), config, paths).problems()).containsExactly(
                new Problem("master-cases.md", "master-cases.md: case ship has by: 'human', which is not master"));
    }

    @Test
    void readsNoCaseWhileTheMasterIsOff(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("off")));

        assertThat(new MasterCaseCheck(new MasterCases(paths), config, paths).problems()).isEmpty();
    }

    @Test
    void refusesToStartWhenTheHumansMasterCasesCannotBeRead(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
        Files.copy(Path.of("../../master-cases.md.dist"), root.resolve("master-cases.md.dist"));
        Files.createDirectory(root.resolve("master-cases.md"));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));

        assertThat(new MasterCaseCheck(new MasterCases(paths), config, paths).problems())
                .extracting(Problem::text).containsExactly(
                        "master-cases.md: cannot be read — java.io.IOException: Is a directory");
    }
}
