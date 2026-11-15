package dev.jagt.orchestrator.surface.ui.log;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Turns {@code orchestrator.logging: console} into the profile {@code logback-spring.xml} switches
 * its appenders on. Registered by hand beside {@link LogFileRoll}, which runs after it.
 */
public class LogTarget
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

  static final String CONSOLE_PROFILE = "jagt-console-logging";

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    apply(event.getEnvironment());
  }

  void apply(ConfigurableEnvironment environment) {
    String chosen = environment.getProperty("orchestrator.logging", "file").trim();
    if (chosen.equals("console")) {
      environment.addActiveProfile(CONSOLE_PROFILE);
    } else if (!chosen.equals("file")) {
      throw new IllegalArgumentException(
          "orchestrator.logging must be file or console, not '" + chosen + "'");
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 14;
  }
}
