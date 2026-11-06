package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferedModelsTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

    @Test
    void offersTheConfiguredListInPlaceOfTheRuntimesOwn() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withAgent(AgentConfig.defaults().withModels(List.of("gpt-5"))));
        when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));

        assertThat(new OfferedModels(configService, agentRuntime).names()).containsExactly("gpt-5");
    }

    @Test
    void offersTheRuntimesOwnListWhenNoneIsConfigured() {
        when(configService.load()).thenReturn(ConfigFile.defaults());
        when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));

        assertThat(new OfferedModels(configService, agentRuntime).names()).containsExactly("opus", "sonnet");
    }

    @Test
    void namesTheAgentCliThatWouldRunThem() {
        when(agentRuntime.displayName()).thenReturn("Codex");

        assertThat(new OfferedModels(configService, agentRuntime).agentName()).isEqualTo("Codex");
    }
}
