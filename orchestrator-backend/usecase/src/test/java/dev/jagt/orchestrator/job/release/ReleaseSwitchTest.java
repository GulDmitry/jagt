package dev.jagt.orchestrator.job.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseSwitchTest {

  private static final String JAR_SUM =
      "fba6a4ac61e440c7309ae013e5c5c7ac0a9000c8a3bab7def760b499d587b1d0  jagt.jar\n";

  @Test
  void pointsCurrentAtTheKeptReleaseWhenItsJarStillMatches(@TempDir Path root) throws Exception {
    Path kept = Files.createDirectories(root.resolve("versions/0.25.0"));
    Files.writeString(kept.resolve("jagt.jar"), "jar bytes");
    Files.writeString(kept.resolve("jagt.jar.sha256"), JAR_SUM);
    FakeCurrentPointer pointer = new FakeCurrentPointer(false);

    ReleaseSwitch.Outcome outcome = new ReleaseSwitch(pointer).switchTo(root, "0.25.0");

    assertThat(outcome).isEqualTo(new ReleaseSwitch.Switched("0.25.0"));
    assertThat(pointer.pointedAt).isEqualTo("0.25.0");
  }

  @Test
  void leavesCurrentWhenTheKeptJarNoLongerMatches(@TempDir Path root) throws Exception {
    Path kept = Files.createDirectories(root.resolve("versions/0.25.0"));
    Files.writeString(kept.resolve("jagt.jar"), "tampered");
    Files.writeString(kept.resolve("jagt.jar.sha256"), JAR_SUM);
    FakeCurrentPointer pointer = new FakeCurrentPointer(false);

    ReleaseSwitch.Outcome outcome = new ReleaseSwitch(pointer).switchTo(root, "0.25.0");

    assertThat(outcome).isEqualTo(new ReleaseSwitch.Refused(ReleaseSwitch.Refusal.MISMATCH));
    assertThat(pointer.pointedAt).isNull();
  }

  @Test
  void leavesCurrentWhenTheReleaseWasKeptWithoutChecksum(@TempDir Path root) throws Exception {
    Path kept = Files.createDirectories(root.resolve("versions/0.25.0"));
    Files.writeString(kept.resolve("jagt.jar"), "jar bytes");
    FakeCurrentPointer pointer = new FakeCurrentPointer(false);

    ReleaseSwitch.Outcome outcome = new ReleaseSwitch(pointer).switchTo(root, "0.25.0");

    assertThat(outcome).isEqualTo(new ReleaseSwitch.Refused(ReleaseSwitch.Refusal.NO_CHECKSUM));
    assertThat(pointer.pointedAt).isNull();
  }

  @Test
  void leavesCurrentWhenTheReleaseIsNotKept(@TempDir Path root) {
    FakeCurrentPointer pointer = new FakeCurrentPointer(false);

    ReleaseSwitch.Outcome outcome = new ReleaseSwitch(pointer).switchTo(root, "0.25.0");

    assertThat(outcome).isEqualTo(new ReleaseSwitch.Refused(ReleaseSwitch.Refusal.NOT_KEPT));
  }

  @Test
  void refusesVersionThatIsNotVersionNumber(@TempDir Path root) {
    FakeCurrentPointer pointer = new FakeCurrentPointer(false);

    ReleaseSwitch.Outcome outcome = new ReleaseSwitch(pointer).switchTo(root, "../elsewhere");

    assertThat(outcome).isEqualTo(new ReleaseSwitch.Refused(ReleaseSwitch.Refusal.NOT_KEPT));
  }

  @Test
  void namesTheFailureWhenCurrentCannotBeChanged(@TempDir Path root) throws Exception {
    Path kept = Files.createDirectories(root.resolve("versions/0.25.0"));
    Files.writeString(kept.resolve("jagt.jar"), "jar bytes");
    Files.writeString(kept.resolve("jagt.jar.sha256"), JAR_SUM);

    ReleaseSwitch.Outcome outcome =
        new ReleaseSwitch(new FakeCurrentPointer(true)).switchTo(root, "0.25.0");

    assertThat(outcome).isEqualTo(new ReleaseSwitch.Refused(ReleaseSwitch.Refusal.NOT_POINTED));
  }
}
