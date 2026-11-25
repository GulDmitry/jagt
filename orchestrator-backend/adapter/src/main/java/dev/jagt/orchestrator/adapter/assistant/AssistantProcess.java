package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Processes;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one way a headless CLI process runs: refused while the breaker is open, and its outcome fed
 * back to it.
 */
@Component
@RequiredArgsConstructor
class AssistantProcess {

  private final Processes runner;
  private final AssistantBreaker breaker;
  private final Host host;

  Path scratchDir() {
    return host.temp();
  }

  Processes.Result run(Path cwd, Duration timeout, List<String> command) {
    breaker.admit();
    Processes.Result result;
    try {
      result = runner.run(cwd, timeout, command);
    } catch (RuntimeException unfinished) {
      breaker.inconclusive();
      throw unfinished;
    }
    ProviderFault.Reading reading = ProviderFault.of(result);
    switch (reading.verdict()) {
      case REACHED -> breaker.succeeded();
      case FAILED -> breaker.failed(reading.cause());
      default -> breaker.inconclusive();
    }
    return result;
  }
}
