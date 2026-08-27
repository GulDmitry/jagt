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
}
