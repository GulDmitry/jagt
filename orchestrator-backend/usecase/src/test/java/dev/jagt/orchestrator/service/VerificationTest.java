package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class VerificationTest {

    @Test
    void runsNothingForAProjectTheConfigurationNoLongerNames(@TempDir Path root) throws IOException {
        Path configFile = Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  projects: {}
                """);
        ConfigService config = new ConfigService(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withConfigFile(configFile.toString())), mock(AgentRuntime.class));
        Processes processes = mock(Processes.class);

        Optional<String> failure = new Verification(config, processes)
                .failure(TaskState.builder("gone", "/wt", TaskStatus.VERIFYING).build());

        assertThat(failure).isEmpty();
        verifyNoInteractions(processes);
    }
}
