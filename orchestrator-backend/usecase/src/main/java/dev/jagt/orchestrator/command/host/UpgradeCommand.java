package dev.jagt.orchestrator.command.host;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.service.release.ReleaseSwitch;
import dev.jagt.orchestrator.service.release.Releases;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpgradeCommand implements GlobalCommand {

  private final Releases releases;
  private final ReleaseSwitch releaseSwitch;

  @Override
  public String id() {
    return "upgrade";
  }

  @Override
  public int rank() {
    return 186;
  }

  @Override
  public String hint() {
    return "switch to the newer release kept beside the running one";
  }

  @Override
  public boolean available() {
    return releases.kept().isPresent();
  }

  @Override
  public Optional<String> whyNotTheArbiter() {
    return Optional.of("an upgrade replaces the install the arbiter runs from");
  }

  @Override
  public String run(String tail) {
    Releases.Kept kept =
        releases.kept().orElseThrow(() -> new IllegalArgumentException("no newer release is kept"));
    return switch (releaseSwitch.toKept(kept)) {
      case ReleaseSwitch.Restarting restarting -> "restarting on " + restarting.version();
      case ReleaseSwitch.Staged staged -> "restart to run " + staged.version();
      case ReleaseSwitch.Refused refused ->
          throw new IllegalArgumentException(
              "upgrade to " + refused.version() + " refused: " + refused.reason());
    };
  }
}
