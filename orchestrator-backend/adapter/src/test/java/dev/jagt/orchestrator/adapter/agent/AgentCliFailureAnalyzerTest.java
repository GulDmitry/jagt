package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.AgentRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCliFailureAnalyzerTest {

    @Test
    void namesTheKeyAndTheValueToFixWhenNoAgentRuntimeAnswersToTheConfiguredCli() {
        AgentCliFailureAnalyzer analyzer = new AgentCliFailureAnalyzer(
                new MockEnvironment().withProperty("orchestrator.agent.cli", "foo"));

        FailureAnalysis analysis = analyzer.analyze(new NoSuchBeanDefinitionException(AgentRuntime.class));

        assertThat(analysis.getDescription()).contains("orchestrator.agent.cli", "'foo'");
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
        List<String> registered = SpringFactoriesLoader.loadFactoryNames(FailureAnalyzer.class,
                getClass().getClassLoader());

        assertThat(registered).contains(AgentCliFailureAnalyzer.class.getName());
    }
}
