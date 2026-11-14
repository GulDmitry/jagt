package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.port.host.Supervisor;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RestartCommand implements GlobalCommand {

  private final Supervisor supervisor;

  @Override
  public String id() {
    return "restart";
  }

  @Override
  public int rank() {
    return 188;
  }

  @Override
  public String hint() {
    return "restart jagt from the staged jar";
  }

  @Override
  public Optional<String> whyNotTheMaster() {
    return Optional.of("a restart ends the Master's own run");
  }

  @Override
  public String run(String tail) {
    return switch (supervisor.restart()) {
      case Supervisor.Supervision.Refused refused ->
          throw new IllegalArgumentException(refused.sentence());
      case Supervisor.Supervision done -> done.sentence();
    };
  }
}
