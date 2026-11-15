package dev.jagt.orchestrator.adapter.values;

import dev.jagt.orchestrator.config.source.ProcessInputs;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Runs once the config files are loaded, so their values are there to read. */
public class ConfiguredValues implements EnvironmentPostProcessor, Ordered {

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    new SpringValues(new ProcessInputs()).apply(environment);
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
