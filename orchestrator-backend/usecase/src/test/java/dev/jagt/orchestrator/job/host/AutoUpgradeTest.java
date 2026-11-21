package dev.jagt.orchestrator.job.host;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.port.host.Exit;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.presence.SessionLedger;
import dev.jagt.orchestrator.service.release.ReleaseSwitch;
import dev.jagt.orchestrator.service.release.Releases;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutoUpgradeTest {

  private static final Releases.Kept KEPT = new Releases.Kept(Path.of("install"), "0.25.0");

  @TempDir Path root;

  private final Releases releases = mock(Releases.class);
  private final Host host = mock(Host.class);
  private final Exit exit = mock(Exit.class);
  private StateService state;

  @BeforeEach
  void keepNewerRelease() {
    state = stateService().withRoot(root).build();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    when(releases.kept()).thenReturn(Optional.of(KEPT));
    when(releases.switchTo(KEPT.root(), KEPT.version()))
        .thenReturn(new Releases.Switched("0.25.0"));
    when(host.env("JAGT_LAUNCHER")).thenReturn(Optional.of("1"));
  }

  @Test
  void switchesAndExitsForTheLauncherWhenNoSessionRuns() {
    new AutoUpgrade(releases, new ReleaseSwitch(releases, host, exit), state, true).attempt();

    verify(releases).switchTo(KEPT.root(), "0.25.0");
    verify(exit).afterAnswer(75);
  }

  @Test
  void waitsWhileAnAgentSessionRuns() {
    new SessionLedger(state, new FakeClock()).sign("ABC-1", new SessionSign.WorkStarted());

    new AutoUpgrade(releases, new ReleaseSwitch(releases, host, exit), state, true).attempt();

    verify(releases, never()).switchTo(KEPT.root(), "0.25.0");
    verify(exit, never()).afterAnswer(75);
  }

  @Test
  void neverSwitchesWhenTheKeyIsOff() {
    new AutoUpgrade(releases, new ReleaseSwitch(releases, host, exit), state, false).attempt();

    verify(releases, never()).switchTo(KEPT.root(), "0.25.0");
  }

  @Test
  void neverSwitchesWhenTheLauncherDidNotStartJagt() {
    when(host.env("JAGT_LAUNCHER")).thenReturn(Optional.empty());

    new AutoUpgrade(releases, new ReleaseSwitch(releases, host, exit), state, true).attempt();

    verify(releases, never()).switchTo(KEPT.root(), "0.25.0");
    verify(exit, never()).afterAnswer(75);
  }

  @Test
  void triesRefusedReleaseOnlyOnce() {
    when(releases.switchTo(KEPT.root(), "0.25.0"))
        .thenReturn(new Releases.Refused(Releases.Refusal.MISMATCH));
    AutoUpgrade autoUpgrade =
        new AutoUpgrade(releases, new ReleaseSwitch(releases, host, exit), state, true);

    autoUpgrade.attempt();
    autoUpgrade.attempt();

    verify(releases, times(1)).switchTo(KEPT.root(), "0.25.0");
    verify(exit, never()).afterAnswer(75);
  }

  @Test
  void triesAnotherReleaseAfterRefusedOne() {
    Releases.Kept next = new Releases.Kept(Path.of("install"), "0.26.0");
    when(releases.switchTo(KEPT.root(), "0.25.0"))
        .thenReturn(new Releases.Refused(Releases.Refusal.NO_CHECKSUM));
    when(releases.switchTo(next.root(), "0.26.0")).thenReturn(new Releases.Switched("0.26.0"));
    AutoUpgrade autoUpgrade =
        new AutoUpgrade(releases, new ReleaseSwitch(releases, host, exit), state, true);
    autoUpgrade.attempt();
    when(releases.kept()).thenReturn(Optional.of(next));

    autoUpgrade.attempt();

    verify(exit).afterAnswer(75);
  }
}
