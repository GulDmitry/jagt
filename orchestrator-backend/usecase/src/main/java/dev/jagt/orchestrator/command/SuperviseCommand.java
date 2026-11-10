package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Supervisor;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperviseCommand implements GlobalCommand {

  private final Supervisor supervisor;
  private final Host host;

  @Override
  public String id() {
    return "supervise";
  }

  @Override
  public int rank() {
    return 189;
  }

  @Override
  public String hint() {
    return "have the machine keep the staged jar running";
  }

  @Override
  public Optional<String> whyNotTheMaster() {
    return Optional.of("installing what restarts jagt is the human's");
  }

  @Override
  public String run(String tail) {
    var jar =
        host.jar()
            .orElseThrow(
                () -> new IllegalArgumentException("this process runs from no single jar"));
    return switch (supervisor.install(jar)) {
      case Supervisor.Supervision.Done done -> done.sentence();
      case Supervisor.Supervision.Refused refused ->
          throw new IllegalArgumentException(refused.sentence());
    };
  }
}
