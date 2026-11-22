package dev.jagt.orchestrator.job.host;

import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.release.ReleaseSwitch;
import dev.jagt.orchestrator.service.release.Releases;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Opt-in switch to the kept release once no agent session runs. A refused version is not tried
 * again; another kept version is.
 */
@Component
@Slf4j
public class AutoUpgrade {

  private final Releases releases;
  private final ReleaseSwitch releaseSwitch;
  private final StateService state;
  private final boolean enabled;
  private volatile @Nullable String refused;

  public AutoUpgrade(
      Releases releases,
      ReleaseSwitch releaseSwitch,
      StateService state,
      @Value("${orchestrator.release-feed.auto-upgrade:false}") boolean enabled) {
    this.releases = releases;
    this.releaseSwitch = releaseSwitch;
    this.state = state;
    this.enabled = enabled;
  }

  public void attempt() {
    if (!enabled || !releaseSwitch.underLauncher()) {
      return;
    }
    releases.kept().filter(kept -> !kept.version().equals(refused)).ifPresent(this::upgrade);
  }

  private void upgrade(Releases.Kept kept) {
    if (sessionRuns()) {
      log.atInfo()
          .setMessage("auto upgrade waits")
          .addKeyValue("ref", kept.version())
          .addKeyValue("effect", "tried again at the next release check")
          .log();
      return;
    }
    if (releaseSwitch.toKept(kept) instanceof ReleaseSwitch.Refused no) {
      refused = no.version();
      log.atWarn()
          .setMessage("auto upgrade refused")
          .addKeyValue("ref", no.version())
          .addKeyValue("reason", no.reason())
          .addKeyValue("effect", "kept, not tried again; upgrade on the board names it")
          .log();
    }
  }

  private boolean sessionRuns() {
    return state.tasks().values().stream()
        .anyMatch(task -> SessionPhase.of(task.work().session()).live());
  }
}
