package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.OrchestratorApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots a real {@code SpringApplication}, and logging initialisation is JVM-wide: run concurrently with another
 * context's boot, Boot's own listener fails with "Unable to find Spring Environment in logger context" — hence
 * the lock every Spring-booting test in this module shares.
 */
@ResourceLock("spring-logging")
class StartupRefusalTest {

    @Test
    void anIncompleteInstallationStopsTheStartInsteadOfServingABoardThatCannotWork(@TempDir Path root)
            throws Exception {
        Files.writeString(root.resolve("mcp_client.js"), "// bridge");
        Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");

        assertThatThrownBy(() -> new SpringApplicationBuilder(OrchestratorApplication.class)
                .run("--server.port=0",
                        "--orchestrator.open-terminal-window=false",
                        "--orchestrator.root=" + root,
                        "--spring.config.import=",
                        "--orchestrator.config-file=" + root.resolve("jagt.yml"),
                        "--orchestrator.state-file=" + root.resolve("state.json"),
                        "--logging.file.name=" + root.resolve("jagt.log")))
                .isInstanceOf(Misconfigured.class)
                .hasMessageContaining("defines no projects");
    }
}
