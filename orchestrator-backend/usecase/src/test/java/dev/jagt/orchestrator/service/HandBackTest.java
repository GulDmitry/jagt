package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HandBackTest {

    @Test
    void owesNoVerificationForAProjectTheConfigurationNoLongerNames(@TempDir Path root) throws IOException {
        Path configFile = Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  projects: {}
                """);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withConfigFile(configFile.toString()));
        ConfigService config = new ConfigService(paths, mock(AgentRuntime.class),
                new ActStops(new MasterBrake(new StateService(new JsonMapper(), paths), paths), new DailyStarts(paths)));
        HandBack handBack = new HandBack(mock(WorktreeChanges.class), mock(Rounds.class), config);

        boolean owed = handBack.verificationOwed(TaskState.builder("gone", "/wt", TaskStatus.IN_PROGRESS).build());

        assertThat(owed).isFalse();
    }

    @Test
    void owesVerificationForATaskCarryingProbesWhereNoProjectCommandIsConfigured(@TempDir Path root) throws IOException {
        Path configFile = Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  projects: {}
                """);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withConfigFile(configFile.toString()));
        ConfigService config = new ConfigService(paths, mock(AgentRuntime.class),
                new ActStops(new MasterBrake(new StateService(new JsonMapper(), paths), paths), new DailyStarts(paths)));
        HandBack handBack = new HandBack(mock(WorktreeChanges.class), mock(Rounds.class), config);

        boolean owed = handBack.verificationOwed(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .probes(List.of(Probe.specified(Probe.Kind.REF, List.of("main"), 0))).build());

        assertThat(owed).isTrue();
    }
}
