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
        Path.of(".."),
        stand,
        StandConfig.Target.local(repo, "http://localhost:1/mcp"),
        "stand-tmux",
        StandMode.JUDGE,
        null);

    assertThat(stand.resolve("roles/acceptance/role.yml.dist")).exists();
  }

  @Test
  void theStandRootHoldsOnlyTheShippedFilesOfRoleNotDevelopersOverride() throws Exception {
    final Path repo = Files.createDirectory(root.resolve("repo"));
    final Path stand = Files.createDirectory(root.resolve("stand"));
    Path source = Files.createDirectories(root.resolve("source/roles/acceptance"));
    Files.createDirectory(source.resolve("eval"));
    Files.writeString(source.resolve("role.yml.dist"), "id: acceptance\n");
    Files.writeString(source.resolve("role.yml"), "id: local\n");
    Files.writeString(source.resolve("eval/01.yml"), "case\n");

    StandConfig.write(
        root.resolve("source"),
        stand,
        StandConfig.Target.local(repo, "http://localhost:1/mcp"),
        "stand-tmux",
        StandMode.JUDGE,
        null);

    assertThat(stand.resolve("roles/acceptance/role.yml")).doesNotExist();
    assertThat(stand.resolve("roles/acceptance/role.yml.dist")).exists();
    assertThat(stand.resolve("roles/acceptance/eval/01.yml")).exists();
  }

  @Test
  void containerTargetClonesFromTheOriginUrlBehindPasswordLogin() throws Exception {
    Path stand = Files.createDirectory(root.resolve("stand"));

    StandConfig.write(
        Path.of(".."),
        stand,
        StandConfig.Target.container(
            stand, "http://host.docker.internal:1/mcp", "http://127.0.0.1:8291"),
        "stand-tmux",
        StandMode.JUDGE,
        null);

    String yml = Files.readString(stand.resolve("jagt.yml"));
    assertThat(yml)
        .contains("url: \"file://" + stand.resolve("origin.git") + "\"")
        .contains("mode: password")
        .contains("${file:/run/secrets/jagt_password_hash}")
        .contains("address: 0.0.0.0")
        .contains("publicUrl: \"http://127.0.0.1:8291\"")
        .doesNotContain("path:");
    assertThat(Files.readString(stand.resolve("servers.json")))
        .contains("http://host.docker.internal:1/mcp");
  }

  @Test
  void localTargetKeepsTheHostPathAndNoLogin() throws Exception {
    Path repo = Files.createDirectory(root.resolve("repo"));
    Path stand = Files.createDirectory(root.resolve("stand"));

    StandConfig.write(
        Path.of(".."),
        stand,
        StandConfig.Target.local(repo, "http://127.0.0.1:1/mcp"),
        "stand-tmux",
        StandMode.JUDGE,
        null);

    assertThat(Files.readString(stand.resolve("jagt.yml")))
        .contains("path: \"" + repo + "\"")
        .doesNotContain("password")
        .doesNotContain("publicUrl");
  }
}
