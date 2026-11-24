package fitness.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MachinePathsTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "cd /mnt/alice/work",
        "cd \\mnt\\alice\\work",
        "~/.claude/projects/-mnt-alice-work/x",
        "see /var/scratch/run-1/out"
      })
  void matchesTheHomeAndDeepTemporaryInEverySpelling(String text) {
    Pattern pattern = MachinePaths.of("/mnt/alice", "/var/scratch/run-1/");

    assertThat(pattern.matcher(text).find()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/tmp/x", "/mnt/bob/x", "/var/scratch/other"})
  void ignoresAShallowTemporaryAndOtherPaths(String text) {
    Pattern pattern = MachinePaths.of("/mnt/alice", "/tmp", "", "/var/scratch/run-1");

    assertThat(pattern.matcher(text).find()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/root/x", "repo-root", "cd \\root"})
  void ignoresAHomeOneLevelDeep(String text) {
    Pattern pattern = MachinePaths.of("/root", "/tmp");

    assertThat(pattern.matcher(text).find()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"D:\\People\\alice\\x", "D:/People/alice/x", "D:-People-alice-x"})
  void matchesADriveHomeInEverySpelling(String text) {
    Pattern pattern = MachinePaths.of("D:\\People\\alice");

    assertThat(pattern.matcher(text).find()).isTrue();
  }
}
