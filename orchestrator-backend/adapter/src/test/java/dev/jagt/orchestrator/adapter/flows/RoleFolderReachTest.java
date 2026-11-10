package dev.jagt.orchestrator.adapter.flows;

import static dev.jagt.orchestrator.adapter.flows.AcceptanceFolders.ENABLED;
import static dev.jagt.orchestrator.adapter.flows.AcceptanceFolders.folder;
import static dev.jagt.orchestrator.adapter.flows.AcceptanceFolders.readOver;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.ReachLedger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoleFolderReachTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "'tools: {git: true}' | names no environment",
        "'{environments: [{url: a}], flows: {driver: selenium}}' | is not one of",
        "'{environments: [{url: a}], flows: {driver: playwright}}' | no flows.path",
        "'{environments: [{url: a}], flows: {driver: playwright, path: /f}}' | no flows.path",
        "'{environments: [{url: a}], flows: {driver: playwright, command: [x]}}' | no flows.path",
        "'{environments: [{url: a}], tools: {mcp: [browser]}}' | tools.mcp browser",
        "'{environments: [{url: a}], flows: {timeout: soon}}' | flows.timeout soon"
      })
  void anEnabledRoleWhoseFolderCannotRunCheckIsOneMarkOnTheBoardsHead(
      String written, String said, @TempDir Path root) throws Exception {
    OrchestratorPaths paths = folder(root, ENABLED);
    Files.writeString(root.resolve("roles/acceptance/role.yml"), written + "\n");

    ReachLedger reach = readOver(paths);

    assertThat(reach.standing())
        .singleElement()
        .satisfies(
            lost -> {
              assertThat(lost.host()).isEqualTo("roles/acceptance/role.yml");
              assertThat(lost.said()).contains(said);
            });
  }

  @Test
  void theMarkEndsOnTheFirstReadAfterTheFolderNamesAnEnvironment(@TempDir Path root)
      throws Exception {
    ReachLedger reach = readOver(folder(root, ENABLED));
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"), "environments: [{url: https://a.test}]\n");

    List<ReachLost> standing = reach.standing();

    assertThat(standing).isEmpty();
  }

  @Test
  void folderNamingAnEnvironmentAndAnMcpServerRaisesNoMark(@TempDir Path root) throws Exception {
    OrchestratorPaths paths = folder(root, ENABLED);
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        "{environments: [{url: a}], tools: {mcp: [mcp__logs]}}\n");

    ReachLedger reach = readOver(paths);

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void roleTurnedOffRaisesNoMarkWhateverItsFolderLacks(@TempDir Path root) throws Exception {
    ReachLedger reach = readOver(folder(root, "orchestrator:\n  projects: {}\n"));

    assertThat(reach.standing()).isEmpty();
  }
}
