package dev.jagt.orchestrator.startup.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionCases;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.service.ConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MasterCaseCheckTest {

  private final ConfigService config = mock(ConfigService.class);

  @TempDir Path root;

  private MasterCaseCheck check;

  @BeforeEach
  void checkOverTheTempRoot() throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(
                    Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n").toString()));
    check = new MasterCaseCheck(new MasterCases(paths), new SessionCases(paths), config, paths);
  }

  @Nested
  class WithTheShippedCases {

    @BeforeEach
    void copyTheShippedCases() throws Exception {
      Files.createDirectories(root.resolve("roles/master"));
      Files.copy(
          Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
      Files.createDirectories(root.resolve("roles/session"));
      Files.copy(
          Path.of("../../roles/session/cases.md.dist"),
          root.resolve("roles/session/cases.md.dist"));
    }

    @Test
    void passesTheShippedCasesAgainstTheMastersMoves() throws Exception {
      when(config.load())
          .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("act")));

      assertThat(check.problems()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"explode", "answered"})
    void refusesMasterCaseNamingAnythingButVerbTheMasterPresses(String move) throws Exception {
      Files.writeString(
          root.resolve("roles/master/cases.md"),
          "## " + move + "\n\nmove: " + move + "\nat: DEPLOYED\nby: master\nThe Master moves.\n");
      when(config.load())
          .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("act")));

      assertThat(check.problems())
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
    void refusesStepTheMasterRelaysNoSessionCaseSetsOffByTheMaster() throws Exception {
      Files.writeString(
          root.resolve("roles/session/cases.md"),
          """
          ## answered-under

          step: answered-under
          at: SHIPPING
          by: human
          replies: any; plain
          You answer below the instruction.
          """);
      when(config.load())
          .thenReturn(
              ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("judge")));

      assertThat(check.problems())
          .containsExactly(
              new Problem(
                  "roles/session/cases.md",
                  "roles/session/cases.md: no case by master sends the Master's move"
                      + " 'answered-under' — add one, or the Master never makes it"));
    }

    @Test
    void refusesMasterCaseSetOffByAnyoneButTheMaster() throws Exception {
      Files.writeString(
          root.resolve("roles/master/cases.md"),
          "## ship\n\nmove: ship\nat: REVIEW_PENDING\nby: human\nYou ship it.\n");
      when(config.load())
          .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("act")));

      assertThat(check.problems())
          .containsExactly(
              new Problem(
                  "roles/master/cases.md",
                  "roles/master/cases.md: case ship has by: 'human', which is not master"));
    }

    @Test
    void refusesToStartWhenTheHumansMasterCasesCannotBeRead() throws Exception {
      Files.createDirectory(root.resolve("roles/master/cases.md"));
      when(config.load())
          .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("act")));

      assertThat(check.problems())
          .extracting(Problem::text)
          .containsExactly(
              "roles/master/cases.md: cannot be read — java.io.IOException: Is a directory");
    }
  }

  @Test
  void refusesVerbNoMasterCaseNames() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(
        root.resolve("roles/master/cases.md.dist"),
        Files.readString(Path.of("../../roles/master/cases.md.dist"))
            .replace("move: add-repo", "move: ship"));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    when(config.load())
        .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("judge")));

    assertThat(check.problems())
        .containsExactly(
            new Problem(
                "roles/master/cases.md",
                "roles/master/cases.md: no case names the Master's move 'add-repo' — add"
                    + " one, or the Master never makes it"));
  }

  @Test
  void readsNoCaseWhileTheMasterIsOff() throws Exception {
    when(config.load())
        .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("off")));

    assertThat(check.problems()).isEmpty();
  }
}
