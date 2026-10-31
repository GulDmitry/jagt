package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandConfigTest {

    @TempDir
    Path root;

    @Test
    void theStandRootHoldsTheShippedRolesSoTheStandsJagtCanStart() throws Exception {
        Path repo = Files.createDirectory(root.resolve("repo"));
        Path stand = Files.createDirectory(root.resolve("stand"));

        StandConfig.write(stand, repo, "http://localhost:1/mcp", "stand-tmux", StandMode.JUDGE, null);

        assertThat(stand.resolve("roles/qa/role.yml.dist")).exists();
    }

    @Test
    void theStandRootHoldsOnlyTheShippedFilesOfARoleNotADevelopersOverride() throws Exception {
        Path repo = Files.createDirectory(root.resolve("repo"));
        Path stand = Files.createDirectory(root.resolve("stand"));
        Path override = Path.of("..", "roles", "qa", "role.yml");
        Files.writeString(Files.createFile(override), "id: local\n");

        try {
            StandConfig.write(stand, repo, "http://localhost:1/mcp", "stand-tmux", StandMode.JUDGE, null);
        } finally {
            Files.delete(override);
        }

        assertThat(stand.resolve("roles/qa/role.yml")).doesNotExist();
        assertThat(stand.resolve("roles/qa/eval/01-all-met.yml")).exists();
    }
}
