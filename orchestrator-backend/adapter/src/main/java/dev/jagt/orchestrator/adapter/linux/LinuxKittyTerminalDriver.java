package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.terminal.AbstractKittyTerminalDriver;
import dev.jagt.orchestrator.adapter.terminal.KittyProperties;
import dev.jagt.orchestrator.port.host.Host;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Both platform hooks are EMPTY by design: {@code kitty @ focus-window} already raises the window,
 * and kitty's own {@code ascii} shortcut fallback handles a non-Latin layout here.
 */
@Component
@ConditionalOnProperty(
    name = "orchestrator.platform",
    havingValue = "linux",
    matchIfMissing = false)
@ConditionalOnProperty(name = "orchestrator.terminal", havingValue = "kitty", matchIfMissing = true)
public class LinuxKittyTerminalDriver extends AbstractKittyTerminalDriver {

  public LinuxKittyTerminalDriver(
      ProcessRunner processRunner, KittyProperties options, Clock clock, Host host) {
    super(processRunner, options, clock, host);
  }

  @Override
  protected List<String> platformOptions() {
    return List.of();
  }

  @Override
  public void bringToFront() {}
}
