package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkspaceCheckTest {

  private final AgentRuntime agent = mock(AgentRuntime.class);

  @Test
  void acceptsRootItCanWriteItsStateInto(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.toString())
            .withStateFile(root.resolve("state.json").toString());

    assertThat(
            new WorkspaceCheck(new OrchestratorPaths(properties, FakeHost.machine()), agent)
                .problems())
        .isEmpty();
  }

  @Test
  void refusesStateFileInDirectoryThatIsNotThere(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.toString())
            .withStateFile(root.resolve("gone").resolve("state.json").toString());

    assertThat(
            new WorkspaceCheck(new OrchestratorPaths(properties, FakeHost.machine()), agent)
                .problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.state-file");
  }

  @Test
  void refusesRootThatIsNotThere(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.resolve("gone").toString())
            .withData(root.toString())
            .withStateFile(root.resolve("state.json").toString());

    assertThat(
            new WorkspaceCheck(new OrchestratorPaths(properties, FakeHost.machine()), agent)
                .problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.root");
  }

  @Test
  void refusesStateDirectoryItCannotWriteInBecauseNoTaskCouldBeRecorded(@TempDir Path root)
      throws Exception {
    Path stateDir = Files.createDirectory(root.resolve("locked"));
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.toString())
            .withStateFile(stateDir.resolve("state.json").toString());
    assertThat(stateDir.toFile().setWritable(false)).isTrue();

    assertThat(
            new WorkspaceCheck(new OrchestratorPaths(properties, FakeHost.machine()), agent)
                .problems())
        .extracting(Problem::key, Problem::text)
        .containsExactly(
            tuple(
                "orchestrator.state-file",
                "orchestrator.state-file "
                    + stateDir.resolve("state.json")
                    + ": "
                    + stateDir
                    + " is not writable, so no task could ever be recorded."));
  }

  @Test
  void refusesStateFileItCannotWrite(@TempDir Path root) throws Exception {
    Path state = Files.createFile(root.resolve("state.json"));
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.toString())
            .withStateFile(state.toString());
    assertThat(state.toFile().setWritable(false)).isTrue();

    assertThat(
            new WorkspaceCheck(new OrchestratorPaths(properties, FakeHost.machine()), agent)
                .problems())
        .extracting(Problem::key, Problem::text)
        .containsExactly(
            tuple(
                "orchestrator.state-file",
                "orchestrator.state-file " + state + " is not writable."));
  }

  @Test
  void refusesDataFolderThatIsNoDirectory(@TempDir Path root) throws Exception {
    Path data = Files.createFile(root.resolve("data"));
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(data.toString())
            .withStateFile(root.resolve("state.json").toString());

    assertThat(
            new WorkspaceCheck(new OrchestratorPaths(properties, FakeHost.machine()), agent)
                .problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.data");
  }

  @ParameterizedTest
  @ValueSource(strings = {"AGENTS.md", "RULES.md"})
  void namesDataFolderAndItsSettingWhenItLiesInsideCheckoutHoldingAnAgentRuleFile(
      String ruleFile, @TempDir Path checkout) throws Exception {
    when(agent.projectAgentFiles()).thenReturn(List.of("AGENTS.md", "RULES.md"));
    Files.createDirectory(checkout.resolve(".git"));
    Files.createFile(checkout.resolve(ruleFile));
    Path data = checkout.resolve("data");
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(checkout.toString())
            .withData(data.toString())
            .withStateFile(checkout.resolve("state.json").toString());

    assertThat(
            new WorkspaceCheck(new OrchestratorPaths(properties, FakeHost.machine()), agent)
                .notes())
        .extracting(Problem::key, Problem::text)
        .containsExactly(
            tuple(
                "orchestrator.data",
                "orchestrator.data "
                    + data
                    + " lies inside the checkout "
                    + checkout
                    + ", whose agent rule files every task session there loads; set"
                    + " orchestrator.data (ORCHESTRATOR_DATA) to a folder outside it."));
  }

  @Test
  void staysSilentWhenCheckoutHoldsNoRuleFileOfTheAgent(@TempDir Path checkout) throws Exception {
    when(agent.projectAgentFiles()).thenReturn(List.of("AGENTS.md"));
    Files.createDirectory(checkout.resolve(".git"));
    Files.createFile(checkout.resolve("OTHER.md"));
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(checkout.toString())
            .withData(checkout.resolve("data").toString());

    assertThat(
            new WorkspaceCheck(new OrchestratorPaths(properties, FakeHost.machine()), agent)
                .notes())
        .isEmpty();
  }

  @Test
  void staysSilentWhenRuleFileSitsOutsideAnyCheckout(@TempDir Path folder) throws Exception {
    when(agent.projectAgentFiles()).thenReturn(List.of("AGENTS.md"));
    Files.createFile(folder.resolve("AGENTS.md"));
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(folder.toString())
            .withData(folder.resolve("data").toString());

    assertThat(
            new WorkspaceCheck(new OrchestratorPaths(properties, FakeHost.machine()), agent)
                .notes())
        .isEmpty();
  }
}
