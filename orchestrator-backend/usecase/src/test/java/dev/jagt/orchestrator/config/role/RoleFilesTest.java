package dev.jagt.orchestrator.config.role;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleFilesTest {

  @Test
  void humansAcceptanceBriefInItsRoleFolderOverridesTheShippedOne(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(
        root.resolve("roles/acceptance/brief.md.dist"), "shipped\n\n## accept\n\nshipped accept\n");
    Files.writeString(
        root.resolve("roles/acceptance/brief.md"), "mine\n\n## accept\n\nmy accept\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());

    RoleFiles files = new RoleFiles(paths, "acceptance");

    assertThat(files.text()).startsWith("mine");
    assertThat(files.section("accept")).contains("my accept");
  }

  @Test
  void theShippedAcceptanceBriefIsReadFromItsRoleFolderWhereTheHumanKeptNoCopy(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(
        root.resolve("roles/acceptance/brief.md.dist"), "shipped\n\n## accept\n\nshipped accept\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());

    RoleFiles files = new RoleFiles(paths, "acceptance");

    assertThat(files.section("accept")).contains("shipped accept");
  }
}
