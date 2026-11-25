package dev.jagt.orchestrator.adapter.herdr;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orchestrator.session-host", havingValue = "herdr")
@RequiredArgsConstructor
public class HerdrCheck implements StartupCheck {

  private final HerdrProperties properties;

  @Override
  public List<Problem> problems() {
    if (Executables.unresolved(Executables.resolve(properties.command()))) {
      return List.of(
          new Problem(
              "orchestrator.herdr.command",
              "orchestrator.session-host is herdr, but orchestrator.herdr.command: '"
                  + properties.command()
                  + "' is not on PATH nor in the usual install directories. Install herdr, set the"
                  + " key to a full path, or set orchestrator.session-host to tmux."));
    }
    return List.of();
  }
}
