package dev.jagt.orchestrator.command.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.job.release.ReleaseCheckJob;
import dev.jagt.orchestrator.job.release.ReleaseSwitch;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UpgradeCommandTest {

  private static final ReleaseCheckJob.Kept KEPT =
      new ReleaseCheckJob.Kept(Path.of("install"), "0.25.0");

  private final ReleaseCheckJob releases = mock(ReleaseCheckJob.class);
  private final ReleaseSwitch release = mock(ReleaseSwitch.class);
  private final UpgradeCommand command = new UpgradeCommand(releases, release);

  @Test
  void isOfferedWhileNewerReleaseIsKept() {
    when(releases.kept()).thenReturn(Optional.of(KEPT));

    assertThat(command.available()).isTrue();
  }

  @Test
  void isNotOfferedWhenNoNewerReleaseIsKept() {
    when(releases.kept()).thenReturn(Optional.empty());

    assertThat(command.available()).isFalse();
  }

  @Test
  void tellsTheHumanToRestartOnceCurrentNamesTheNewVersion() {
    when(releases.kept()).thenReturn(Optional.of(KEPT));
    when(release.switchTo(Path.of("install"), "0.25.0"))
        .thenReturn(new ReleaseSwitch.Switched("0.25.0"));

    String reply = command.run("");

    assertThat(reply).isEqualTo("restart to run 0.25.0");
    verify(releases).switched("0.25.0");
  }

  @Test
  void namesTheRefusalWhenTheSwitchIsRefused() {
    when(releases.kept()).thenReturn(Optional.of(KEPT));
    when(release.switchTo(Path.of("install"), "0.25.0"))
        .thenReturn(new ReleaseSwitch.Refused(ReleaseSwitch.Refusal.MISMATCH));

    assertThatThrownBy(() -> command.run(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("upgrade to 0.25.0 refused: MISMATCH");
  }

  @Test
  void isRefusedWhenNothingNewerIsKept() {
    when(releases.kept()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> command.run("")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isTheHumansAloneSinceItReplacesTheInstallTheArbiterRunsFrom() {
    assertThat(command.whyNotTheArbiter()).isPresent();
  }
}
