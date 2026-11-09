package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandConfigTest {

  @TempDir Path root;

  @Test
  void theStandRootHoldsTheShippedRolesSoTheStandsJagtCanStart() throws Exception {
    Path repo = Files.createDirectory(root.resolve("repo"));
    Path stand = Files.createDirectory(root.resolve("stand"));

    StandConfig.write(
        Path.of(".."), stand, repo, "http://localhost:1/mcp", "stand-tmux", StandMode.JUDGE, null);

    assertThat(stand.resolve("roles/acceptance/role.yml.dist")).exists();
  }

  @Test
  void theStandRootHoldsOnlyTheShippedFilesOfARoleNotADevelopersOverride() throws Exception {
    Path repo = Files.createDirectory(root.resolve("repo"));
    Path stand = Files.createDirectory(root.resolve("stand"));
    Path source = Files.createDirectories(root.resolve("source/roles/acceptance"));
    Files.createDirectory(source.resolve("eval"));
    Files.writeString(source.resolve("role.yml.dist"), "id: acceptance\n");
    Files.writeString(source.resolve("role.yml"), "id: local\n");
    Files.writeString(source.resolve("eval/01.yml"), "case\n");

    StandConfig.write(
        root.resolve("source"),
        stand,
        repo,
        "http://localhost:1/mcp",
        "stand-tmux",
        StandMode.JUDGE,
        null);

    assertThat(stand.resolve("roles/acceptance/role.yml")).doesNotExist();
    assertThat(stand.resolve("roles/acceptance/role.yml.dist")).exists();
    assertThat(stand.resolve("roles/acceptance/eval/01.yml")).exists();
  }
}
