package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.terminal.KittyOptions;
import dev.jagt.orchestrator.testing.FakeHost;
import java.time.Clock;
import lombok.With;

@With
public record TestKittyTerminalDriverBuilder(KittyOptions options) {

  public static TestKittyTerminalDriverBuilder kittyDriver() {
    return new TestKittyTerminalDriverBuilder(new KittyOptions("/k/kitty", ""));
  }

  public KittyTerminalDriver build(ProcessRunner runner, OsaScript osaScript, Clock clock) {
    return new KittyTerminalDriver(runner, osaScript, options, clock, FakeHost.machine());
  }
}
