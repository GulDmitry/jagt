package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RoleFilesTest {

    @Test
    void aHumansOldQaBriefStillOverridesTheShippedOne(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("roles/qa"));
        Files.writeString(root.resolve("roles/qa/brief.md.dist"), "shipped\n\n## accept\n\nshipped accept\n");
        Files.writeString(root.resolve("qa-brief.md"), "mine\n\n## accept\n\nmy accept\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        RoleFiles files = new RoleFiles(paths, "qa");

        assertThat(files.text()).startsWith("mine");
        assertThat(files.section("accept")).contains("my accept");
    }

    @Test
    void theShippedQaBriefIsReadFromItsRoleFolderWhereTheHumanKeptNoCopy(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("roles/qa"));
        Files.writeString(root.resolve("roles/qa/brief.md.dist"), "shipped\n\n## accept\n\nshipped accept\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        RoleFiles files = new RoleFiles(paths, "qa");

        assertThat(files.section("accept")).contains("shipped accept");
    }
}
