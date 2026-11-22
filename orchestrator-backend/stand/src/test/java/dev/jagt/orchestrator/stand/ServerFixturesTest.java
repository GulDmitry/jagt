package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerFixturesTest {

  private static final Path SHIPPED = Path.of(System.getProperty("stand.repo", "../.."));

  @TempDir Path dir;

  @Test
  void bareCloneOfOneFileProjectAndHashOfPasswordAreWritten() throws Exception {
    ServerFixtures.write(SHIPPED, new ServerFixtures.Where(dir, dir, 8290, true), "pw");

    assertThat(dir.resolve("origin.git/HEAD")).exists();
    assertThat(StandShell.git(dir, "--git-dir", "origin.git", "show", "main:a.txt"))
        .isEqualTo("hi\n");
    assertThat(dir.resolve("password-hash")).content().startsWith("$2");
  }

  @Test
  void theFakeAgentPostsToThePortJagtListensOnAndCanBeRun() throws Exception {
    ServerFixtures.write(SHIPPED, new ServerFixtures.Where(dir, dir, 40123, true), "pw");

    assertThat(dir.resolve("fake-claude.sh")).content().contains("/dev/tcp/127.0.0.1/40123");
    assertThat(Files.isExecutable(dir.resolve("fake-claude.sh"))).isTrue();
  }

  @Test
  void theStarterNamesTheFixturesWhereJagtSeesThem() throws Exception {
    ServerFixtures.write(
        SHIPPED, new ServerFixtures.Where(dir, Path.of("/data/stand"), 8290, true), "pw");

    assertThat(dir.resolve("jagt.yml"))
        .content()
        .contains("url: /data/stand/origin.git")
        .contains("passwordHash: ${file:/data/stand/password-hash}")
        .contains("publicUrl: http://127.0.0.1:8290")
        .contains("cookie.secure: false")
        .contains("command: \"/data/stand/fake-claude.sh\"");
  }

  @Test
  void masterIsTurnedOffInTheStartersOwnRolesKeyNotAddedTwice() throws Exception {
    ServerFixtures.write(SHIPPED, new ServerFixtures.Where(dir, dir, 8290, true), "pw");

    assertThat(dir.resolve("jagt.yml"))
        .content()
        .containsOnlyOnce("    master:")
        .contains("    master:\n      mode: \"off\"");
  }

  @Test
  void hostRunLeavesThePlatformToBeDetected() throws Exception {
    ServerFixtures.write(SHIPPED, new ServerFixtures.Where(dir, dir, 8290, false), "pw");

    assertThat(dir.resolve("jagt.yml")).content().doesNotContain("platform: linux");
  }

  @Test
  void theShippedRolesAreInstalledForRootThatIsJagtsOwn() throws Exception {
    ServerFixtures.write(SHIPPED, new ServerFixtures.Where(dir, dir, 8290, true), "pw");

    assertThat(dir.resolve("roles")).isDirectory();
  }

  @Test
  void starterThatLostLineTheFixturesEditFailsNamingIt() throws Exception {
    Path shipped = Files.createDirectories(dir.resolve("shipped"));
    Files.writeString(shipped.resolve("jagt.server.yml.dist"), "orchestrator: {}\n");
    Files.createDirectories(shipped.resolve("roles"));
    Path root = Files.createDirectories(dir.resolve("root"));

    assertThatThrownBy(() ->
            ServerFixtures.write(shipped, new ServerFixtures.Where(root, root, 8290, true), "pw"))
        .isInstanceOf(StandFailure.class)
        .hasMessage("jagt.server.yml.dist no longer holds https://example.com/team/repo.git");
  }
}
