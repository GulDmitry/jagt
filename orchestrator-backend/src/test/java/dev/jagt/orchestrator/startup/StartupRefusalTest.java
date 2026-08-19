package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.OrchestratorApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupRefusalTest {

    @Test
    void anIncompleteInstallationStopsTheStartInsteadOfServingABoardThatCannotWork(@TempDir Path root)
            throws Exception {
        Files.writeString(root.resolve("mcp_client.js"), "// bridge");
        Files.writeString(root.resolve("config.json"), "{ \"projects\": {} }");

        assertThatThrownBy(() -> new SpringApplicationBuilder(OrchestratorApplication.class)
                .run("--server.port=0",
                        "--orchestrator.ui=web",
                        "--orchestrator.open-warp-window=false",
                        "--orchestrator.root=" + root,
                        "--orchestrator.config-file=" + root.resolve("config.json"),
                        "--orchestrator.state-file=" + root.resolve("state.json"),
                        "--logging.file.name=" + root.resolve("jagt.log")))
                .isInstanceOf(Misconfigured.class)
                .hasMessageContaining("config.json defines no projects");
    }
}
