package dev.jagt.orchestrator.surface.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterTokenTest {

  @Test
  void admitsWhatItLeftBesideTheStateForTheRootsOwnConfigOnly(@TempDir Path root) throws Exception {
    MasterToken token =
        new MasterToken(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

    String written = Files.readString(root.resolve("master-token"));

    assertThat(token.matches(written)).isTrue();
    assertThat(token.matches(written + "0")).isFalse();
    assertThat(token.matches("")).isFalse();
    assertThat(
            PosixFilePermissions.toString(
                Files.getPosixFilePermissions(root.resolve("master-token"))))
        .isEqualTo("rw-------");
  }

  @Test
  void startsWhenTheRootIsReadOnlyAndTheStateFolderWritable(@TempDir Path root, @TempDir Path state)
      throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(state.resolve("state.json").toString()));
    Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("r-xr-xr-x"));

    MasterToken token = new MasterToken(paths);

    assertThat(token.matches(Files.readString(state.resolve("master-token")))).isTrue();
    Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  @Test
  void drawsNewTokenAtEachStart(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    new MasterToken(paths);
    String first = Files.readString(root.resolve("master-token"));

    MasterToken restarted = new MasterToken(paths);

    assertThat(restarted.matches(first)).isFalse();
  }
}
