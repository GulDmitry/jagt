package dev.jagt.orchestrator.command.host;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.port.host.Exit;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Supervisor;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperviseCommand implements GlobalCommand {

  private final Supervisor supervisor;
  private final Host host;
  private final Exit exit;

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
  public Optional<String> whyNotTheArbiter() {
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
      case Supervisor.Supervision.Handover handover -> {
        exit.afterAnswer();
        yield handover.sentence();
      }
      case Supervisor.Supervision.Refused refused ->
          throw new IllegalArgumentException(refused.sentence());
    };
  }
}
