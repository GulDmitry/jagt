package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.terminal.KittyOptions;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import java.time.Clock;
import lombok.With;

@With
public record TestKittyTerminalDriverBuilder(
    OrchestratorProperties properties, KittyOptions options) {

  public static TestKittyTerminalDriverBuilder kittyDriver() {
    return new TestKittyTerminalDriverBuilder(
        OrchestratorProperties.defaults(), new KittyOptions("/k/kitty", ""));
  }

  public KittyTerminalDriver build(ProcessRunner runner, OsaScript osaScript, Clock clock) {
    return new KittyTerminalDriver(runner, properties, osaScript, options, clock);
  }
}
