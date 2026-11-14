package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.host.Supervisor;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "orchestrator",
    name = "supervisor",
    havingValue = "none",
    matchIfMissing = true)
public class NoSupervisor implements Supervisor {

  private static final Supervision REFUSED =
      new Supervision.Refused("no supervisor is chosen: set `supervisor` in jagt.yml");

  @Override
  public boolean supervised() {
    return false;
  }

  @Override
  public Supervision install(Path stagedJar) {
    return REFUSED;
  }

  @Override
  public Supervision restart() {
    return REFUSED;
  }
}
