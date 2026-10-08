package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HandBackTest {

    @Test
    void owesNoVerificationForAProjectTheConfigurationNoLongerNames(@TempDir Path root) throws IOException {
        Path configFile = Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  projects: {}
                """);
        ConfigService config = new ConfigService(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withConfigFile(configFile.toString())), mock(AgentRuntime.class));
        HandBack handBack = new HandBack(mock(WorktreeChanges.class), mock(Rounds.class), config);

        boolean owed = handBack.verificationOwed(TaskState.builder("gone", "/wt", TaskStatus.IN_PROGRESS).build());

        assertThat(owed).isFalse();
    }
}
