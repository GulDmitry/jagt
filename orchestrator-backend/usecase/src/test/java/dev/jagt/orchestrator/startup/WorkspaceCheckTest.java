package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceCheckTest {

    @Test
    void acceptsARootItCanWriteItsStateInto(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString());

        assertThat(new WorkspaceCheck(new OrchestratorPaths(properties)).problems()).isEmpty();
    }

    @Test
    void refusesAStateFileInADirectoryThatIsNotThere(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("gone").resolve("state.json").toString());

        assertThat(new WorkspaceCheck(new OrchestratorPaths(properties)).problems())
                .extracting(Problem::key).containsExactly("orchestrator.state-file");
    }

    @Test
    void refusesARootThatIsNotThere(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.resolve("gone").toString())
                .withStateFile(root.resolve("state.json").toString());

        assertThat(new WorkspaceCheck(new OrchestratorPaths(properties)).problems())
                .extracting(Problem::key).containsExactly("orchestrator.root");
    }
}
