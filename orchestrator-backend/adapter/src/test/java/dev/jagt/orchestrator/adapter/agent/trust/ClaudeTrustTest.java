package dev.jagt.orchestrator.adapter.agent.trust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ClaudeTrustTest {

  @Test
  void acceptsTheTrustDialogForWorktreeClaudeHasNeverRunIn() {
    String updated = ClaudeTrust.accepted("{}", "/wt/ABC-42");

    boolean trusted =
        new JsonMapper()
            .readTree(updated)
            .path("projects")
            .path("/wt/ABC-42")
            .path("hasTrustDialogAccepted")
            .asBoolean(false);

    assertThat(trusted).isTrue();
  }

  @Test
  void suiteRunTrustsItsWorktreesInsideTheBuildNeverInTheHumansConfig() {
    Path configFile = ClaudeTrust.configFile();

    assertThat(configFile).startsWithRaw(Path.of("").toAbsolutePath());
  }

  @Test
  void trustsEveryWorktreeWhenManyAreProvisionedAtOnce(@TempDir Path dir) throws Exception {
    Path configFile = dir.resolve(".claude.json");
    Path worktrees = Files.createDirectories(dir.resolve("wt"));

    java.util.stream.IntStream.range(0, 40)
        .parallel()
        .forEach(n -> ClaudeTrust.accept(configFile, worktrees.resolve("ABC-" + n)));

    assertThat(new JsonMapper().readTree(Files.readString(configFile)).path("projects").size())
        .isEqualTo(40);
  }

  @Test
  void waitsWhileAnotherJagtProcessIsWritingTheConfig(@TempDir Path dir) throws Exception {
    Path configFile = dir.resolve(".claude.json");
    Path holder =
        Files.writeString(
            dir.resolve("Holder.java"),
            """
            class Holder {
                public static void main(String[] args) throws Exception {
                    try (var channel = java.nio.channels.FileChannel.open(java.nio.file.Path.of(args[0]),
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
                         var held = channel.lock()) {
                        System.out.println("held");
                        System.in.read();
                    }
                }
            }
            """);
    Process otherJagt =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                holder.toString(),
                dir.resolve(".claude.json.jagt-lock").toString())
            .start();
    assertThat(otherJagt.inputReader().readLine()).isEqualTo("held");

    Path worktree = Path.of("/wt/ABC-42");
    var writing =
        CompletableFuture.runAsync(() -> ClaudeTrust.accept(configFile, worktree)); // allowed: 0237

    assertThatThrownBy(() -> writing.get(1, java.util.concurrent.TimeUnit.SECONDS))
        .isInstanceOf(java.util.concurrent.TimeoutException.class);
    otherJagt.getOutputStream().close();
    writing.get(30, java.util.concurrent.TimeUnit.SECONDS);
    assertThat(
            new JsonMapper()
                .readTree(Files.readString(configFile))
                .path("projects")
                .path("/wt/ABC-42")
                .path("hasTrustDialogAccepted")
                .asBoolean(false))
        .isTrue();
  }

  @Test
  void writesNothingWhenTheWorktreeIsAlreadyTrusted() {
    String updated =
        ClaudeTrust.accepted(
            "{\"projects\": {\"/wt/ABC-42\": {\"hasTrustDialogAccepted\": true}}}", "/wt/ABC-42");

    assertThat(updated).isNull();
  }

  @Test
  void keepsEveryOtherProjectWhenTrustingWorktree() {
    String updated =
        ClaudeTrust.accepted(
            "{\"numStartups\": 7, \"projects\": {\"/other\": {\"lastCost\": 3}}}", "/wt/ABC-42");

    var root = new JsonMapper().readTree(updated);

    assertThat(root.path("numStartups").asInt(0)).isEqualTo(7);
    assertThat(root.path("projects").path("/other").path("lastCost").asInt(0)).isEqualTo(3);
  }

  @Test
  void recordsTheAcceptanceWhenClaudeHasWrittenNoConfigYet(@TempDir Path home) throws Exception {
    ClaudeTrust.accept(home.resolve(".claude.json"), Path.of("/wt/ABC-42"));

    boolean trusted =
        new JsonMapper()
            .readTree(Files.readString(home.resolve(".claude.json")))
            .path("projects")
            .path("/wt/ABC-42")
            .path("hasTrustDialogAccepted")
            .asBoolean(false);

    assertThat(trusted).isTrue();
  }

  @Test
  void leavesConfigItCannotUnderstandUntouched(@TempDir Path home) throws Exception {
    Path config = home.resolve(".claude.json");
    Files.writeString(config, "[]");

    ClaudeTrust.accept(config, home);

    assertThat(Files.readString(config)).isEqualTo("[]");
  }

  @Test
  void dropsTheEntryOfRetiredWorktree() {
    String updated =
        ClaudeTrust.forgotten(
            "{\"projects\": {\"/wt/ABC-42\": {\"hasTrustDialogAccepted\": true}, \"/other\": {}}}",
            "/wt/ABC-42");

    var projects = new JsonMapper().readTree(updated).path("projects");

    assertThat(projects.has("/wt/ABC-42")).isFalse();
    assertThat(projects.has("/other")).isTrue();
  }

  @Test
  void writesNothingWhenTheRetiredWorktreeHasNoEntry() {
    String updated = ClaudeTrust.forgotten("{\"projects\": {\"/other\": {}}}", "/wt/ABC-42");

    assertThat(updated).isNull();
  }

  @Test
  void keysTheEntryByTheRealPathOfWorktreeReachedThroughLink(@TempDir Path home) throws Exception {
    Path real = Files.createDirectory(home.resolve("real"));
    Path link = Files.createSymbolicLink(home.resolve("link"), real);
    Path config = home.resolve(".claude.json");

    ClaudeTrust.accept(config, link);

    var projects = new JsonMapper().readTree(Files.readString(config)).path("projects");
    assertThat(projects.has(real.toRealPath().toString())).isTrue();
    assertThat(projects.size()).isEqualTo(1);
  }

  @Test
  void keysTheEntryOfDeletedWorktreeByItsParentsRealPath(@TempDir Path home) throws Exception {
    Path real = Files.createDirectory(home.resolve("real"));
    Path link = Files.createSymbolicLink(home.resolve("link"), real);
    Path config = home.resolve(".claude.json");

    ClaudeTrust.accept(config, link.resolve("gone"));

    var projects = new JsonMapper().readTree(Files.readString(config)).path("projects");
    assertThat(projects.has(real.toRealPath().resolve("gone").toString())).isTrue();
    assertThat(projects.size()).isEqualTo(1);
  }

  @Test
  void keysTheEntryOfDeletedRelativeWorktreeByItsOwnPath(@TempDir Path home) throws Exception {
    Path config = home.resolve(".claude.json");

    ClaudeTrust.accept(config, Path.of("no-such-worktree-anywhere"));

    var projects = new JsonMapper().readTree(Files.readString(config)).path("projects");
    assertThat(projects.has("no-such-worktree-anywhere")).isTrue();
  }

  @Test
  void forgetsTheEntryOfWorktreeThatStillExists(@TempDir Path home) throws Exception {
    Path worktree = Files.createDirectory(home.resolve("wt"));
    Path config = home.resolve(".claude.json");
    ClaudeTrust.accept(config, worktree);

    ClaudeTrust.forget(config, worktree);

    assertThat(new JsonMapper().readTree(Files.readString(config)).path("projects").size())
        .isZero();
  }

  @Test
  void warnsNothingOnceTheTrustFlagStuck(@TempDir Path home) {
    try (FakeLogCapture log = new FakeLogCapture(ClaudeTrust.class)) {
      ClaudeTrust.reaccept(home.resolve(".claude.json"), home);

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .doesNotContain("trust flag kept being lost");
      assertThat(home.resolve(".claude.json")).exists();
    }
  }

  @Test
  void warnsOnceTheTrustFlagKeptBeingLost(@TempDir Path home) throws Exception {
    Path config = home.resolve(".claude.json");
    Files.writeString(config, "[]");
    try (FakeLogCapture log = new FakeLogCapture(ClaudeTrust.class)) {
      ClaudeTrust.reaccept(config, home);

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .contains("trust flag kept being lost");
    }
  }

  @Test
  void warnsWhenTheConfigCannotBeReadSoTheTrustFlagCannotBeConfirmed(@TempDir Path home)
      throws Exception {
    Path config = home.resolve(".claude.json");
    Files.writeString(config, "{broken");
    try (FakeLogCapture log = new FakeLogCapture(ClaudeTrust.class)) {
      ClaudeTrust.reaccept(config, home);

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .contains("trust flag kept being lost");
    }
  }

  @Test
  void forgetsNothingWhereTheConfigHoldsNoProjects() {
    assertThat(ClaudeTrust.forgotten("{}", "/wt/ABC-42")).isNull();
  }
}
