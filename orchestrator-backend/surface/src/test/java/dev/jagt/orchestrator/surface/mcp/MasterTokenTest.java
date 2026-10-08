package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;

class MasterTokenTest {

    @Test
    void admitsWhatItLeftForTheRootsOwnConfigOnly(@TempDir Path root) throws Exception {
        MasterToken token = new MasterToken(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())));

        String written = Files.readString(root.resolve(".jagt/master-token"));

        assertThat(token.matches(written)).isTrue();
        assertThat(token.matches(written + "0")).isFalse();
        assertThat(token.matches(null)).isFalse();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(root.resolve(".jagt/master-token"))))
                .isEqualTo("rw-------");
    }

    @Test
    void drawsANewTokenAtEachStart(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        new MasterToken(paths);
        String first = Files.readString(root.resolve(".jagt/master-token"));

        MasterToken restarted = new MasterToken(paths);

        assertThat(restarted.matches(first)).isFalse();
    }
}
