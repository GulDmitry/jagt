package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Every OS-specific piece is picked from the platform, the machine's unless overridden; an override
 * naming another OS starts perfectly and fails where nobody is looking.
 */
@Component
public class PlatformCheck implements StartupCheck {

  private static final String KEY = "orchestrator.platform";

  private final OrchestratorProperties properties;
  private final String osName;

  public PlatformCheck(OrchestratorProperties properties, Host host) {
    this.properties = properties;
    this.osName = host.osName();
  }

  @Override
  public List<Problem> problems() {
    List<Problem> problems = new ArrayList<>();
    String running = running();
    String configured = configured();
    if (running == null) {
      problems.add(
          new Problem(
              KEY,
              "orchestrator.platform: jagt drives macOS and Linux, and this machine"
                  + " reports '"
                  + osName
                  + "' — there is no platform to set it to."));
    } else if (!running.equals(configured)) {
      problems.add(
          new Problem(
              KEY,
              "orchestrator.platform is '"
                  + configured
                  + "' on a machine reporting '"
                  + osName
                  + "': its notifier binary does not exist here, so no alert would reach you."
                  + " Set it to '"
                  + running
                  + "'."));
    }
    return problems;
  }

  private @Nullable String configured() {
    String platform = properties.platform();
    return platform == null || platform.isBlank() ? running() : platform.toLowerCase(Locale.ROOT);
  }

  private @Nullable String running() {
    return DetectedPlatform.of(osName).orElse(null);
  }
}
