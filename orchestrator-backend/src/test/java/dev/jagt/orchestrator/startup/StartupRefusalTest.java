package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.OrchestratorApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;

class StartupRefusalTest {

  @Test
  void anIncompleteInstallationStopsTheStartInsteadOfServingBoardThatCannotWork(@TempDir Path root)
      throws Exception {
    Files.writeString(root.resolve("mcp_client.js"), "// bridge");
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    Files.copy(
        Path.of("../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");

    assertThatThrownBy(() ->
            new SpringApplicationBuilder(OrchestratorApplication.class)
                .run(
                    "--server.port=0",
                    "--orchestrator.terminal=none",
                    "--orchestrator.root=" + root,
                    "--orchestrator.data=" + root,
                    "--spring.config.import=",
                    "--orchestrator.config-file=" + root.resolve("jagt.yml"),
                    "--orchestrator.state-file=" + root.resolve("state.json"),
                    "--logging.file.name=" + root.resolve("jagt.log")))
        .isInstanceOf(Misconfigured.class)
        .hasMessageContaining("defines no projects");
  }
}
