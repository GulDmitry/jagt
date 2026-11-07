package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.AgentRuntime;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@Order(Ordered.HIGHEST_PRECEDENCE)
class AgentCliFailureAnalyzer extends AbstractFailureAnalyzer<NoSuchBeanDefinitionException> {

  private static final String KEY = "orchestrator.agent.cli";

  private final Environment environment;

  AgentCliFailureAnalyzer(Environment environment) {
    this.environment = environment;
  }

  @Override
  protected @Nullable FailureAnalysis analyze(
      Throwable rootFailure, NoSuchBeanDefinitionException cause) {
    if (!AgentRuntime.class.equals(cause.getBeanType())) {
      return null;
    }
    return new FailureAnalysis(
        KEY
            + " is '"
            + environment.getProperty(KEY, "")
            + "', which no agent CLI jagt ships answers to.",
        "Set agent.cli in jagt.yml to an agent CLI jagt ships, or remove it; jagt.yml.dist lists"
            + " them.",
        cause);
  }
}
