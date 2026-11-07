package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.adapter.HeldCommand;
import dev.jagt.orchestrator.port.KeepAwake;
import dev.jagt.orchestrator.port.Processes;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code -w} ends the assertion with this process, however it dies. */
@Component
@ConditionalOnProperty(
    prefix = "orchestrator",
    name = "platform",
    havingValue = "macos",
    matchIfMissing = true)
public class Caffeinate implements KeepAwake {

  private final HeldCommand assertion;

  public Caffeinate(Processes processes) {
    this.assertion =
        new HeldCommand(
            processes,
            List.of(
                Executables.resolve("caffeinate"),
                "-i",
                "-w",
                Long.toString(ProcessHandle.current().pid())));
  }

  @Override
  public void hold() {
    assertion.hold();
  }

  @Override
  @PreDestroy
  public void release() {
    assertion.release();
  }
}
