package dev.jagt.orchestrator.service.release;

import dev.jagt.orchestrator.port.host.Exit;
import dev.jagt.orchestrator.port.host.Host;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReleaseSwitch {

  /** What switching to a kept release came to. */
  public sealed interface Result {}

  /** {@code current} names the version and jagt exits for the launcher to start it. */
  public record Restarting(String version) implements Result {}

  /** {@code current} names the version; nothing would start jagt again, so it keeps running. */
  public record Staged(String version) implements Result {}

  /** {@code current} is unchanged. */
  public record Refused(String version, Releases.Refusal reason) implements Result {}

  private final Releases releases;
  private final Host host;
  private final Exit exit;

  public boolean underLauncher() {
    return host.env(Exit.LAUNCHER_ENV).isPresent();
  }

  public Result toKept(Releases.Kept kept) {
    return switch (releases.switchTo(kept.root(), kept.version())) {
      case Releases.Switched switched -> {
        releases.switched(switched.version());
        if (underLauncher()) {
          exit.afterAnswer(Exit.RUN_AGAIN);
          yield new Restarting(switched.version());
        }
        yield new Staged(switched.version());
      }
      case Releases.Refused refused -> new Refused(kept.version(), refused.reason());
    };
  }
}
