package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorPathsTest {

    @Test
    void takesTheFileNamedOnTheCommandLineOverTheOneItWouldHaveGoneLookingFor(@TempDir Path root) {
        Path named = root.resolve("elsewhere").resolve("jagt.yml");

        assertThat(OrchestratorPaths.configFileOutside(new String[]{"--orchestrator.config-file=" + named}))
                .isEqualTo(named);
    }

    @Test
    void answersInsideTheRootItWasGivenRatherThanWalkingUpForAMarker(@TempDir Path root) {
        assertThat(OrchestratorPaths.configFileOutside(new String[]{"--orchestrator.root=" + root}))
                .isEqualTo(root.resolve("jagt.yml"));
    }

    @Test
    void keepsThePinnedFileWhenTheConfigItselfMovesTheRoot(@TempDir Path root) throws Exception {
        Path pinned = Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n");
        OrchestratorProperties moved = OrchestratorProperties.defaults()
                .withRoot(root.resolve("somewhere-else").toString())
                .withConfigFile(pinned.toString());

        assertThat(new OrchestratorPaths(moved).configFile()).isEqualTo(pinned);
    }
}
