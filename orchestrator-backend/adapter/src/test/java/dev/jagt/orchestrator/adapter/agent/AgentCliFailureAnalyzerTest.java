package dev.jagt.orchestrator.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

class AgentCliFailureAnalyzerTest {

  @Test
  void namesTheKeyAndTheValueToFixWhenNoAgentRuntimeAnswersToTheConfiguredCli() {
    AgentCliFailureAnalyzer analyzer =
        new AgentCliFailureAnalyzer(
            new MockEnvironment().withProperty("orchestrator.agent.cli", "foo"));

    FailureAnalysis analysis =
        analyzer.analyze(new NoSuchBeanDefinitionException(AgentRuntime.class));

    assertThat(Objects.requireNonNull(analysis).getDescription())
        .contains("orchestrator.agent.cli", "'foo'");
    assertThat(analysis.getAction()).contains("agent.cli", "jagt.yml");
  }

  @Test
  void staysSilentWhenTheMissingBeanIsNotAnAgentRuntime() {
    AgentCliFailureAnalyzer analyzer = new AgentCliFailureAnalyzer(new MockEnvironment());

    FailureAnalysis analysis = analyzer.analyze(new NoSuchBeanDefinitionException(String.class));

    assertThat(analysis).isNull();
  }

  @Test
  void isRegisteredWithTheStartupFailureReporting() {
    List<String> registered =
        SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
            .load(
                FailureAnalyzer.class,
                SpringFactoriesLoader.ArgumentResolver.of(Environment.class, new MockEnvironment()),
                SpringFactoriesLoader.FailureHandler.handleMessage((message, failure) -> {}))
            .stream()
            .map(analyzer -> analyzer.getClass().getName())
            .toList();

    assertThat(registered).contains(AgentCliFailureAnalyzer.class.getName());
  }
}
