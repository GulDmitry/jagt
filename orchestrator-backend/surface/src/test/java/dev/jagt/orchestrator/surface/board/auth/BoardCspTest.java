package dev.jagt.orchestrator.surface.board.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.surface.board.files.BoardFolder;
import dev.jagt.orchestrator.surface.board.files.BoardProperties;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoardCspTest {

  @TempDir Path root;

  @Test
  void hashesTheImportMapOfTheBoardFolderWhenBoardDirDiffersFromTheJar() throws Exception {
    Path board = Files.createDirectories(root.resolve("my-board"));
    Files.createDirectories(board.resolve("vendor/codemirror"));
    String page = "<script type=\"importmap\">{\"imports\":{\"a\":\"/a.js\"}}</script>";
    Files.writeString(board.resolve("index.html"), page);
    Files.writeString(board.resolve("styleguide.html"), page);
    Files.writeString(
        board.resolve("vendor/codemirror/importmap.json"), "{\"imports\":{\"b\":\"/b.js\"}}");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());
    BoardCsp csp = new BoardCsp(new BoardFolder(new BoardProperties("my-board"), paths));

    String policy = csp.policy();

    assertThat(policy).contains("'sha256-+q/WiRau/ts/QoAL1BbUDQHiuI1hDmKvdtcVWR5N5AQ='");
  }
}
