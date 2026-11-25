package dev.jagt.orchestrator.job.checker.workspace;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.RoleSettings;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CheckerWorkspaceTest {

  @TempDir Path root;
  private final WorkspaceRequests requests = mock(WorkspaceRequests.class);
  private Path folder;
  private CheckerWorkspace workspace;

  @BeforeEach
  void setUp() throws Exception {
    folder = Files.createDirectories(root.resolve("roles/acceptance"));
    RoleDefinition role = RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"));
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withRoles(Map.of("acceptance", new ConfigFile.RoleConfig(true, null))));
    workspace =
        new CheckerWorkspace(
            role,
            new RoleSettings(
                role,
                config,
                new OrchestratorPaths(
                    OrchestratorProperties.defaults().withRoot(root.toString()),
                    FakeHost.machine())),
            requests);
  }

  @Test
  void theRolesWorkspaceIsProposedWhereItsMergeRequestIsOn() throws Exception {
    Files.writeString(folder.resolve("role.yml"), "workspace:\n  path: ws\n  mergeRequest: true\n");

    workspace.offer("ABC-42");

    verify(requests).propose("acceptance", folder.toAbsolutePath().resolve("ws"), "ABC-42");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "workspace: ws\n",
        "workspace:\n  path: ws\n  mergeRequest: false\n",
        "workspace: none\n",
        "workspace:\n  path: none\n  mergeRequest: true\n"
      })
  void nothingIsProposedWhereTheMergeRequestIsOffOrTheRoleHoldsNoWorkspace(String written)
      throws Exception {
    Files.writeString(folder.resolve("role.yml"), written);

    workspace.offer("ABC-42");

    verifyNoInteractions(requests);
  }
}
