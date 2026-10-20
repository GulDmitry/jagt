package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeTrustTest {

    @Test
    void acceptsTheTrustDialogForAWorktreeClaudeHasNeverRunIn() {
        String updated = ClaudeTrust.accepted("{}", "/wt/ABC-42");

        boolean trusted = new JsonMapper().readTree(updated)
                .path("projects").path("/wt/ABC-42").path("hasTrustDialogAccepted").asBoolean(false);

        assertThat(trusted).isTrue();
    }

    @Test
    void aSuiteRunTrustsItsWorktreesInsideTheBuildNeverInTheHumansConfig() {
        Path configFile = ClaudeTrust.configFile();

        assertThat(configFile).startsWithRaw(Path.of("").toAbsolutePath());
    }

    @Test
    void writesNothingWhenTheWorktreeIsAlreadyTrusted() {
        String updated = ClaudeTrust.accepted(
                "{\"projects\": {\"/wt/ABC-42\": {\"hasTrustDialogAccepted\": true}}}", "/wt/ABC-42");

        assertThat(updated).isNull();
    }

    @Test
    void keepsEveryOtherProjectWhenTrustingAWorktree() {
        String updated = ClaudeTrust.accepted(
                "{\"numStartups\": 7, \"projects\": {\"/other\": {\"lastCost\": 3}}}", "/wt/ABC-42");

        var root = new JsonMapper().readTree(updated);

        assertThat(root.path("numStartups").asInt(0)).isEqualTo(7);
        assertThat(root.path("projects").path("/other").path("lastCost").asInt(0)).isEqualTo(3);
    }

    @Test
    void recordsTheAcceptanceWhenClaudeHasWrittenNoConfigYet(@TempDir Path home) throws Exception {
        ClaudeTrust.accept(home.resolve(".claude.json"), Path.of("/wt/ABC-42"));

        boolean trusted = new JsonMapper().readTree(Files.readString(home.resolve(".claude.json")))
                .path("projects").path("/wt/ABC-42").path("hasTrustDialogAccepted").asBoolean(false);

        assertThat(trusted).isTrue();
    }

    @Test
    void leavesAConfigItCannotUnderstandUntouched(@TempDir Path home) throws Exception {
        Path config = home.resolve(".claude.json");
        Files.writeString(config, "[]");

        ClaudeTrust.accept(config, home);

        assertThat(Files.readString(config)).isEqualTo("[]");
    }

    @Test
    void dropsTheEntryOfARetiredWorktree() {
        String updated = ClaudeTrust.forgotten(
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
    void keysTheEntryByTheRealPathOfAWorktreeReachedThroughALink(@TempDir Path home) throws Exception {
        Path real = Files.createDirectory(home.resolve("real"));
        Path link = Files.createSymbolicLink(home.resolve("link"), real);
        Path config = home.resolve(".claude.json");

        ClaudeTrust.accept(config, link);

        var projects = new JsonMapper().readTree(Files.readString(config)).path("projects");
        assertThat(projects.has(real.toRealPath().toString())).isTrue();
        assertThat(projects.size()).isEqualTo(1);
    }

    @Test
    void keysTheEntryOfADeletedWorktreeByItsParentsRealPath(@TempDir Path home) throws Exception {
        Path real = Files.createDirectory(home.resolve("real"));
        Path link = Files.createSymbolicLink(home.resolve("link"), real);
        Path config = home.resolve(".claude.json");

        ClaudeTrust.accept(config, link.resolve("gone"));

        var projects = new JsonMapper().readTree(Files.readString(config)).path("projects");
        assertThat(projects.has(real.toRealPath().resolve("gone").toString())).isTrue();
        assertThat(projects.size()).isEqualTo(1);
    }

    @Test
    void keysTheEntryOfADeletedRelativeWorktreeByItsOwnPath(@TempDir Path home) throws Exception {
        Path config = home.resolve(".claude.json");

        ClaudeTrust.accept(config, Path.of("no-such-worktree-anywhere"));

        var projects = new JsonMapper().readTree(Files.readString(config)).path("projects");
        assertThat(projects.has("no-such-worktree-anywhere")).isTrue();
    }

    @Test
    void forgetsTheEntryOfAWorktreeThatStillExists(@TempDir Path home) throws Exception {
        Path worktree = Files.createDirectory(home.resolve("wt"));
        Path config = home.resolve(".claude.json");
        ClaudeTrust.accept(config, worktree);

        ClaudeTrust.forget(config, worktree);

        assertThat(new JsonMapper().readTree(Files.readString(config)).path("projects").size()).isZero();
    }
}
