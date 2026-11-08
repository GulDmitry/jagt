package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MasterCaseCheckTest {

  private final ConfigService config = mock(ConfigService.class);

  @Test
  void passesTheShippedCasesAgainstTheMastersMoves(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(
                    Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act")));

    assertThat(
            new MasterCaseCheck(new MasterCases(paths), new SessionCases(paths), config, paths)
                .problems())
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"explode", "answered"})
  void refusesAMasterCaseNamingAnythingButAVerbTheMasterPresses(String move, @TempDir Path root)
      throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(
                    Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.writeString(
        root.resolve("roles/master/cases.md"),
        "## " + move + "\n\nmove: " + move + "\nat: DEPLOYED\nby: master\nThe Master moves.\n");
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act")));

    assertThat(
            new MasterCaseCheck(new MasterCases(paths), new SessionCases(paths), config, paths)
                .problems())
        .containsExactly(
            new Problem(
                "roles/master/cases.md",
                "roles/master/cases.md: case "
                    + move
                    + " names move '"
                    + move
                    + "', which is no verb the Master presses"));
  }

  @Test
  void refusesAVerbNoMasterCaseNames(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(
                    Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(
        root.resolve("roles/master/cases.md.dist"),
        Files.readString(Path.of("../../roles/master/cases.md.dist"))
            .replace("move: add-repo", "move: ship"));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("judge")));

    assertThat(
            new MasterCaseCheck(new MasterCases(paths), new SessionCases(paths), config, paths)
                .problems())
        .containsExactly(
            new Problem(
                "roles/master/cases.md",
                "roles/master/cases.md: no case names the Master's move 'add-repo' — add"
                    + " one, or the Master never makes it"));
  }

  @Test
  void refusesAStepTheMasterRelaysNoSessionCaseSetsOffByTheMaster(@TempDir Path root)
      throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(
                    Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        "## answered-under\n\nstep: answered-under\n"
            + "at: SHIPPING\nby: human\nreplies: any; plain\nYou answer below the instruction.\n");
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("judge")));

    assertThat(
            new MasterCaseCheck(new MasterCases(paths), new SessionCases(paths), config, paths)
                .problems())
        .containsExactly(
            new Problem(
                "roles/session/cases.md",
                "roles/session/cases.md: no case by master sends the Master's move"
                    + " 'answered-under' — add one, or the Master never makes it"));
  }

  @Test
  void refusesAMasterCaseSetOffByAnyoneButTheMaster(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(
                    Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    Files.writeString(
        root.resolve("roles/master/cases.md"),
        "## ship\n\nmove: ship\nat: REVIEW_PENDING\nby: human\nYou ship it.\n");
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act")));

    assertThat(
            new MasterCaseCheck(new MasterCases(paths), new SessionCases(paths), config, paths)
                .problems())
        .containsExactly(
            new Problem(
                "roles/master/cases.md",
                "roles/master/cases.md: case ship has by: 'human', which is not master"));
  }

  @Test
  void readsNoCaseWhileTheMasterIsOff(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(
                    Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("off")));

    assertThat(
            new MasterCaseCheck(new MasterCases(paths), new SessionCases(paths), config, paths)
                .problems())
        .isEmpty();
  }

  @Test
  void refusesToStartWhenTheHumansMasterCasesCannotBeRead(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(
                    Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectory(root.resolve("roles/master/cases.md"));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act")));

    assertThat(
            new MasterCaseCheck(new MasterCases(paths), new SessionCases(paths), config, paths)
                .problems())
        .extracting(Problem::text)
        .containsExactly(
            "roles/master/cases.md: cannot be read — java.io.IOException: Is a directory");
  }
}
