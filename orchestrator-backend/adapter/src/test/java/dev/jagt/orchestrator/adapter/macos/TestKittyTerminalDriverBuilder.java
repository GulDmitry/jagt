package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.terminal.KittyProperties;
import dev.jagt.orchestrator.testing.FakeHost;
import java.time.Clock;
import lombok.With;

@With
public record TestKittyTerminalDriverBuilder(KittyProperties options) {

  public static TestKittyTerminalDriverBuilder kittyDriver() {
    return new TestKittyTerminalDriverBuilder(new KittyProperties("/k/kitty", ""));
  }

  public KittyTerminalDriver build(ProcessRunner runner, OsaScript osaScript, Clock clock) {
    return new KittyTerminalDriver(runner, osaScript, options, clock, FakeHost.machine());
  }
}
