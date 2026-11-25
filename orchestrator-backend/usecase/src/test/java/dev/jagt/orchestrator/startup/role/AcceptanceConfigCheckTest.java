package dev.jagt.orchestrator.startup.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.KeptNumbers;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.startup.Misconfigured;
import dev.jagt.orchestrator.startup.StartupValidation;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import dev.jagt.orchestrator.testing.FakeHost;
import dev.jagt.orchestrator.testing.FakeReach;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AcceptanceConfigCheckTest {

  @TempDir Path root;

  AcceptanceConfigCheck check;

  @BeforeEach
  void checkOverTheTempRoot() {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withConfigFile(root.resolve("jagt.yml").toString())
            .withStateFile(root.resolve("state.json").toString());
    OrchestratorPaths paths = new OrchestratorPaths(properties, FakeHost.machine());
    check =
        new AcceptanceConfigCheck(
            new ConfigService(
                new ConfigSource(paths, new FakeConfigInputs()),
                mock(AgentRuntime.class),
                mock(ActStops.class),
                new Checkers(new TaskChart(Composition.builtIn())),
                new KeptNumbers(new ReachLedger())),
            paths);
  }

  @ParameterizedTest
  @MethodSource
  void namesKeyNothingReadsAnyMoreSoAnOldConfigDoesNotGoOnPromisingIt(String key, String expected)
      throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  " + key + "\n");

    assertThat(check.problems()).extracting(Problem::key).containsExactly(expected);
  }

  static Stream<Arguments> namesKeyNothingReadsAnyMoreSoAnOldConfigDoesNotGoOnPromisingIt() {
    return Stream.of(
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
  void refusesTheStartNamingRolesAcceptanceWhereTheRoleIsStillWrittenAsQa(String key)
      throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  " + key + "\n");

    assertThatThrownBy(
            new StartupValidation(List.of(check), new FakeReach(), true)
                ::refuseToRunWithoutWhatItNeeds)
        .isInstanceOf(Misconfigured.class)
        .hasMessageContaining("roles.acceptance");
  }
}
