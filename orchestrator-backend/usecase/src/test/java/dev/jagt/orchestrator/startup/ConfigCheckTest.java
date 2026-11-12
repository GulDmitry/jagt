package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigCheckTest {

  @TempDir Path root;

  SessionHost sessions = mock(SessionHost.class);
  ConfigCheck check;

  @BeforeEach
  void checkOverTheTempRoot() {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withConfigFile(root.resolve("jagt.yml").toString())
            .withStateFile(root.resolve("state.json").toString());
    check =
        new ConfigCheck(
            new ConfigService(
                new OrchestratorPaths(properties),
                mock(AgentRuntime.class),
                mock(ActStops.class),
                new Checkers(new TaskChart(Composition.builtIn()))),
            new OrchestratorPaths(properties),
            sessions,
            new WorkingDirHost(root));
  }

  @Test
  void acceptsProjectThatPointsAtRealRepository() throws Exception {
    Files.createDirectories(root.resolve("repo").resolve(".git"));
    Files.writeString(
        root.resolve("jagt.yml"),
        """
        orchestrator:
          viewer: { tmuxSession: jagt, viewMode: tab-per-task }
          projects:
            demo: { path: "%s", baseBranch: origin/main, deployBranch: dev }
        """
            .formatted(root.resolve("repo")));
    assertThat(check.problems())
        .isEmpty();
  }

  @Test
  void saysWhereTheConfigFileShouldBeWhenThereIsNone() {
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.config-file");
  }

  @ParameterizedTest
  @MethodSource
  void namesKeyNothingReadsAnyMoreSoAnOldConfigDoesNotGoOnPromisingIt(
      String key, String expected) throws Exception {
    Files.createDirectories(root.resolve("repo").resolve(".git"));
    Files.writeString(
        root.resolve("jagt.yml"),
        """
        orchestrator:
          %s
          projects:
            demo: { path: "%s", baseBranch: origin/main, deployBranch: dev }
        """
            .formatted(key, root.resolve("repo")));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly(expected);
  }

  static Stream<Arguments> namesKeyNothingReadsAnyMoreSoAnOldConfigDoesNotGoOnPromisingIt() {
    return Stream.of(
        Arguments.of("ui: tui", "orchestrator.ui"),
        Arguments.of("terminal: warp", "orchestrator.terminal"),
        Arguments.of("openWarpWindow: false", "orchestrator.openWarpWindow"),
        Arguments.of("webTerminal: { enabled: true }", "orchestrator.webTerminal"),
        Arguments.of("dashboard: { refreshSeconds: 10 }", "orchestrator.dashboard"),
        Arguments.of("qa: { enabled: true }", "orchestrator.qa"),
        Arguments.of("roles: { qa: { enabled: true } }", "orchestrator.roles.qa"),
        Arguments.of(
            "roles: { acceptance: { enabled: true, model: opus } }",
            "orchestrator.roles.acceptance.model"),
        Arguments.of(
            "roles: { acceptance: { environments: [] } }",
            "orchestrator.roles.acceptance.environments"),
        Arguments.of(
            "roles: { acceptance: { flows: { driver: none } } }",
            "orchestrator.roles.acceptance.flows"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"qa: { enabled: true }", "roles: { qa: { enabled: true } }"})
  void refusesTheStartNamingRolesAcceptanceWhereTheRoleIsStillWrittenAsQa(
      String key) throws Exception {
    Files.createDirectories(root.resolve("repo").resolve(".git"));
    Files.writeString(
        root.resolve("jagt.yml"),
        """
        orchestrator:
          %s
          projects:
            demo: { path: "%s", baseBranch: origin/main, deployBranch: dev }
        """
            .formatted(key, root.resolve("repo")));
    assertThatThrownBy(new StartupValidation(List.of(check), true)::refuseToRunWithoutWhatItNeeds)
        .isInstanceOf(Misconfigured.class)
        .hasMessageContaining("roles.acceptance");
  }

  @ParameterizedTest
  @MethodSource
  void namesWhatConfiguredProjectWouldFailOn(String config, String expected) throws Exception {
    Files.createDirectories(root.resolve("repo").resolve(".git"));
    Files.createDirectories(root.resolve("plain-dir"));
    Files.writeString(root.resolve("jagt.yml"), config.replace("$ROOT", root.toString()));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly(expected);
  }

  static Stream<Arguments> namesWhatConfiguredProjectWouldFailOn() {
    return Stream.of(
        Arguments.of(
            """
            orchestrator:
              projects: {}
            """,
            "orchestrator.projects"),
        Arguments.of(
            """
            orchestrator:
              projects: { demo: { path: "", baseBranch: origin/main } }
            """,
            "orchestrator.projects.demo.path"),
        Arguments.of(
            """
            orchestrator:
              projects: { demo: { path: $ROOT/gone, baseBranch: origin/main } }
            """,
            "orchestrator.projects.demo.path"),
        Arguments.of(
            """
            orchestrator:
              projects: { demo: { path: $ROOT/plain-dir, baseBranch: origin/main } }
            """,
            "orchestrator.projects.demo.path"),
        Arguments.of(
            """
            orchestrator:
              projects: { demo: { path: $ROOT/repo } }
            """,
            "orchestrator.projects.demo.baseBranch"),
        Arguments.of(
            """
            orchestrator:
              projects:
                demo: { path: $ROOT/repo, baseBranch: origin/main, deployBranch: main }
            """,
            "orchestrator.projects.demo.deployBranch"),
        Arguments.of(
            """
            orchestrator:
              viewer: { viewMode: one-per-task }
              projects: { demo: { path: $ROOT/repo, baseBranch: origin/main } }
            """,
            "orchestrator.viewer.viewMode"));
  }

  @Test
  void saysTheOldConfigFileIsNoLongerReadWhereNoJagtYmlTookItsPlace()
      throws Exception {
    Files.writeString(root.resolve("config.json"), "{}");
    assertThat(check.problems())
        .singleElement()
        .satisfies(
            problem -> {
              assertThat(problem.key()).isEqualTo("orchestrator.config-file");
              assertThat(problem.text()).contains("config.json is no longer read");
            });
  }

  @Test
  void ignoresTheOldConfigFileOnceJagtYmlHoldsTheProjects() throws Exception {
    Files.createDirectories(root.resolve("repo").resolve(".git"));
    Files.writeString(root.resolve("config.json"), "{}");
    Files.writeString(
        root.resolve("jagt.yml"),
        """
        orchestrator:
          projects:
            demo: { path: "%s", baseBranch: origin/main, deployBranch: dev }
        """
            .formatted(root.resolve("repo")));
    assertThat(check.problems())
        .isEmpty();
  }

  @Test
  void refusesViewerSessionNameTheHostCannotAddress() throws Exception {
    Files.createDirectories(root.resolve("repo").resolve(".git"));
    Files.writeString(
        root.resolve("jagt.yml"),
        """
        orchestrator:
          viewer: { tmuxSession: "my.session" }
          projects:
            demo: { path: "%s", baseBranch: origin/main, deployBranch: dev }
        """
            .formatted(root.resolve("repo")));
    when(sessions.unaddressable("my.session")).thenReturn(Optional.of("holds a dot"));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.viewer.tmuxSession");
  }

  @Test
  void refusesConfigDirectoryThatOutranksJagtYmlForEveryKeyItNames()
      throws Exception {
    Path shadowing =
        Files.writeString(
            Files.createDirectories(root.resolve("config")).resolve("application.yml"),
            "orchestrator: {}\n");
    assertThat(check.problems())
        .containsExactly(
            new Problem(
                "config/application.yml",
                shadowing
                    + " outranks jagt.yml for every key it"
                    + " names. Move what it holds into jagt.yml and delete it."));
  }
}
