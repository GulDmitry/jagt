package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandConfigReplayTest {

  @TempDir Path root;

  @Test
  void replayConfigurationNamesTheRecordingAndSwitchesToTheReplayAgent() throws Exception {
    Path repo = Files.createDirectory(root.resolve("repo"));
    Path stand = Files.createDirectory(root.resolve("stand"));
    Path recording = Files.writeString(root.resolve("recording.jsonl"), "{}\n");

    StandConfig.write(
        Path.of(System.getProperty("stand.repo", "../..")),
        stand,
        StandConfig.Target.local(repo, "http://127.0.0.1:1/mcp"),
        "stand-tmux",
        StandMode.ACT,
        new StandConfig.Replay(recording, "http://127.0.0.1:2/app"));

    assertThat(Files.readString(stand.resolve("jagt.yml")))
        .contains("cli: replay")
        .contains("codeHost: replay")
        .contains("replay: { file: \"" + recording + "\" }")
        .doesNotContain("flows:");
  }

  @Test
  void recordingOfAnAcceptanceCheckEnablesTheAcceptanceRoleWithTheShippedFlows() throws Exception {
    Path repo = Files.createDirectory(root.resolve("repo"));
    Path stand = Files.createDirectory(root.resolve("stand"));
    Path recording =
        Files.writeString(root.resolve("recording.jsonl"), "{\"kind\":\"ACCEPTANCE_CHECK\"}\n");

    StandConfig.write(
        Path.of(System.getProperty("stand.repo", "../..")),
        stand,
        StandConfig.Target.local(repo, "http://127.0.0.1:1/mcp"),
        "stand-tmux",
        StandMode.JUDGE,
        new StandConfig.Replay(recording, "http://127.0.0.1:2/app"));

    assertThat(Files.readString(stand.resolve("jagt.yml")))
        .contains("roles: { acceptance: { enabled: true } }");
    assertThat(Files.readString(stand.resolve("roles/acceptance/role.yml")))
        .contains("http://127.0.0.1:2/app")
        .contains("driver: playwright");
    assertThat(stand.resolve("flows/flows.sh")).exists();
  }
}
