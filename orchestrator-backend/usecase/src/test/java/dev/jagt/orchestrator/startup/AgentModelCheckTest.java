package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentModelCheckTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

    @Test
    void refusesAModelTheRuntimeDoesNotListNamingIt() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withAgent(AgentConfig.defaults().withModel("gpt")));
        when(agentRuntime.choosesModel()).thenReturn(true);
        when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));

        List<Problem> problems = new AgentModelCheck(configService, agentRuntime).problems();

        assertThat(problems).extracting(Problem::key).containsExactly("orchestrator.agent.model");
        assertThat(problems.get(0).text()).contains("'gpt'");
    }

    @Test
    void acceptsAModelTheRuntimeLists() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withAgent(AgentConfig.defaults().withModel("sonnet")));
        when(agentRuntime.choosesModel()).thenReturn(true);
        when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));

        assertThat(new AgentModelCheck(configService, agentRuntime).problems()).isEmpty();
    }

    @Test
    void ignoresTheModelForARuntimeThatCannotChoose() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withAgent(AgentConfig.defaults().withModel("sonnet")));

        assertThat(new AgentModelCheck(configService, agentRuntime).problems()).isEmpty();
    }

    @Test
    void acceptsABlankModelWhateverTheRuntime() {
        when(configService.load()).thenReturn(ConfigFile.defaults());

        assertThat(new AgentModelCheck(configService, agentRuntime).problems()).isEmpty();
    }
}
