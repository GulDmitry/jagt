package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstalledRolesTest {

  @Test
  void aFolderWithABriefAndNoRoleFileIsAReaderAndNeverAChecker(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/shipped"));
    Files.writeString(root.resolve("roles/shipped/brief.md.dist"), "a brief\n");
    Files.createDirectories(root.resolve("roles/copied"));
    Files.writeString(root.resolve("roles/copied/brief.md"), "a brief\n");
    Files.createDirectories(root.resolve("roles/bare"));
    Files.createDirectories(root.resolve("roles/checker"));
    Files.writeString(root.resolve("roles/checker/brief.md.dist"), "a brief\n");
    Files.writeString(root.resolve("roles/checker/role.yml.dist"), "id: checker\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    InstalledRoles installed = new InstalledRoles(paths);

    assertThat(installed.readers()).containsExactly("copied", "shipped");
    assertThat(installed.definitions()).isEmpty();
  }
}
