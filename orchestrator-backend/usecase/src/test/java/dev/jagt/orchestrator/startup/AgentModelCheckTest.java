package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.AgentConfig;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentModelCheckTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

  @Test
  void refusesModelTheRuntimeDoesNotListNamingIt() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withAgent(AgentConfig.defaults().withModel("gpt")));
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));

    List<Problem> problems = new AgentModelCheck(configService, agentRuntime).problems();

    assertThat(problems).extracting(Problem::key).containsExactly("orchestrator.agent.model");
    assertThat(problems.get(0).text()).contains("'gpt'");
  }

  @Test
  void acceptsModelTheRuntimeLists() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withAgent(AgentConfig.defaults().withModel("sonnet")));
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));

    assertThat(new AgentModelCheck(configService, agentRuntime).problems()).isEmpty();
  }

  @Test
  void ignoresTheModelForRuntimeThatCannotChoose() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withAgent(AgentConfig.defaults().withModel("sonnet")));

    assertThat(new AgentModelCheck(configService, agentRuntime).problems()).isEmpty();
  }

  @Test
  void acceptsBlankModelWhateverTheRuntime() {
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThat(new AgentModelCheck(configService, agentRuntime).problems()).isEmpty();
  }

  @Test
  void acceptsModelTheConfigListsWhereTheRuntimeListsNone() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(
                    AgentConfig.defaults()
                        .withModel("gpt-5")
                        .withModels(List.of("gpt-5", "gpt-5-mini"))));

    assertThat(new AgentModelCheck(configService, agentRuntime).problems()).isEmpty();
  }

  @Test
  void refusesModelOutsideTheConfigListEvenWhenTheRuntimeListsIt() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(AgentConfig.defaults().withModel("opus").withModels(List.of("gpt-5"))));
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));

    List<Problem> problems = new AgentModelCheck(configService, agentRuntime).problems();

    assertThat(problems.get(0).text()).contains("'opus'").contains("[gpt-5]");
  }

  @Test
  void keepsTheRuntimeListWhenTheConfigListIsEmpty() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(AgentConfig.defaults().withModel("opus").withModels(List.of())));
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));

    assertThat(new AgentModelCheck(configService, agentRuntime).problems()).isEmpty();
  }
}
