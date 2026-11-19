package dev.jagt.orchestrator.command.host;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.job.release.ReleaseCheckJob;
import dev.jagt.orchestrator.job.release.ReleaseSwitch;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpgradeCommand implements GlobalCommand {

  private final ReleaseCheckJob releases;
  private final ReleaseSwitch release;

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
    ReleaseCheckJob.Kept kept =
        releases.kept().orElseThrow(() -> new IllegalArgumentException("no newer release is kept"));
    return switch (release.switchTo(kept.root(), kept.version())) {
      case ReleaseSwitch.Switched switched -> {
        releases.switched(switched.version());
        yield "restart to run " + switched.version();
      }
      case ReleaseSwitch.Refused refused ->
          throw new IllegalArgumentException(
              "upgrade to " + kept.version() + " refused: " + refused.reason());
    };
  }
}
