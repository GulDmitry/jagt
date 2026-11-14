package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "supervisor", havingValue = "launchd")
@RequiredArgsConstructor
public class LaunchdCheck implements StartupCheck {

  private static final String KEY = "orchestrator.supervisor";

  private final Host host;

  @Override
  public List<Problem> problems() {
    if (!host.osName().toLowerCase(Locale.ROOT).contains("mac")) {
      return List.of(
          new Problem(
              KEY,
              "orchestrator.supervisor is 'launchd' on a machine reporting '"
                  + host.osName()
                  + "': launchd exists on macOS only. Set it to 'none'."));
    }
    if (!host.onPath("launchctl")) {
      return List.of(
          new Problem(KEY, "orchestrator.supervisor is 'launchd' but `launchctl` is not on PATH."));
    }
    return List.of();
  }
}
