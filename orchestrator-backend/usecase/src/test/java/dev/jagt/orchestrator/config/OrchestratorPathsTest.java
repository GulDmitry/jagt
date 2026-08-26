package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A launch has to name the config file before a Spring context exists, and the bean then resolves it again.
 * Two answers to "which jagt.yml" is the split the single-file configuration was built to end.
 */
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

    /**
     * The value a launch pins is what the BEAN must then answer with, whatever the file it points at says about
     * the root — otherwise Spring binds one jagt.yml while everything else reads a second one under the new root.
     */
    @Test
    void keepsThePinnedFileWhenTheConfigItselfMovesTheRoot(@TempDir Path root) throws Exception {
        Path pinned = Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n");
        OrchestratorProperties moved = OrchestratorProperties.defaults()
                .withRoot(root.resolve("somewhere-else").toString())
                .withConfigFile(pinned.toString());

        assertThat(new OrchestratorPaths(moved).configFile()).isEqualTo(pinned);
    }
}
