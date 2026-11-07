package dev.jagt.orchestrator.surface.ui;

import java.util.Optional;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public class PortGuard
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    refusal(event.getEnvironment())
        .ifPresent(
            line -> {
              Console.problem(line);
              System.exit(1);
            });
  }

  Optional<String> refusal(ConfigurableEnvironment environment) {
    String port = BoardPort.configured(environment);
    return BoardPort.taken(port)
        ? Optional.of(
            "start refused port="
                + port
                + " cause=\"another jagt or process holds it\""
                + " fix=\"--server.port=<port>\"")
        : Optional.empty();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }
}
