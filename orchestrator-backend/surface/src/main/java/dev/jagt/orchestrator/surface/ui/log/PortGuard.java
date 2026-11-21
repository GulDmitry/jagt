package dev.jagt.orchestrator.surface.ui.log;

import dev.jagt.orchestrator.surface.ui.Console;
import java.util.Optional;
import java.util.function.IntConsumer;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public class PortGuard
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

  // Runs before any context exists, so the context-bound exit seam is not there yet; the log
  // roll must not move another jagt's open file.
  private final IntConsumer exit;

  public PortGuard(IntConsumer exit) {
    this.exit = exit;
  }

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    refusal(event.getEnvironment())
        .ifPresent(line -> {
          Console.problem(line);
          exit.accept(1);
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
