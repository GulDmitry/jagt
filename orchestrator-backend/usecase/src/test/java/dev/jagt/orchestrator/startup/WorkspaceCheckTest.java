package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

    @Test
    void refusesAStateDirectoryItCannotWriteInBecauseNoTaskCouldBeRecorded(@TempDir Path root) throws Exception {
        Path stateDir = Files.createDirectory(root.resolve("locked"));
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(stateDir.resolve("state.json").toString());
        assertThat(stateDir.toFile().setWritable(false)).isTrue();

        assertThat(new WorkspaceCheck(new OrchestratorPaths(properties)).problems())
                .extracting(Problem::key, Problem::text)
                .containsExactly(tuple("orchestrator.state-file", "orchestrator.state-file "
                        + stateDir.resolve("state.json") + ": " + stateDir
                        + " is not writable, so no task could ever be recorded."));
    }

    @Test
    void refusesAStateFileItCannotWrite(@TempDir Path root) throws Exception {
        Path state = Files.createFile(root.resolve("state.json"));
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(state.toString());
        assertThat(state.toFile().setWritable(false)).isTrue();

        assertThat(new WorkspaceCheck(new OrchestratorPaths(properties)).problems())
                .extracting(Problem::key, Problem::text)
                .containsExactly(tuple("orchestrator.state-file", "orchestrator.state-file " + state
                        + " is not writable."));
    }
}
