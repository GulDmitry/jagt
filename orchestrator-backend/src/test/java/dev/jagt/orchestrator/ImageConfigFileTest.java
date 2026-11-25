package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImageConfigFileTest {

  private static final Path DOCKERFILE = Path.of("../Dockerfile");
  private static final Path ENTRYPOINT = Path.of("../entrypoint.sh");

  @Test
  void imageReadsItsConfigurationFromTheVolumeAndShipsOnlySeed() {
    String dockerfile = SourceRoots.read(DOCKERFILE);

    assertThat(dockerfile)
        .contains(
            "ORCHESTRATOR_CONFIG_FILE=/data/jagt.yml", "COPY jagt.server.yml.dist jagt.seed.yml")
        .doesNotContain("jagt.server.yml.dist jagt.yml");
  }

  @Test
  void startSeedsConfigurationOnlyWhereNoneStands() {
    assertThat(SourceRoots.read(ENTRYPOINT))
        .contains(
            "[ ! -e \"$ORCHESTRATOR_CONFIG_FILE\" ]", "cp \"$seed\" \"$ORCHESTRATOR_CONFIG_FILE\"");
  }

  @Test
  void startOverwritesConfigurationFromImageOnlyWhenAskedTo() {
    assertThat(SourceRoots.read(ENTRYPOINT))
        .contains(
            "{ [ ! -e \"$ORCHESTRATOR_CONFIG_FILE\" ]",
            "|| [ \"$JAGT_CONFIG_FROM_IMAGE\" = true ]; }");
  }

  @Test
  void upgradeSeedsVolumeFromConfigurationMountedWhereOldImageReadIt() {
    assertThat(SourceRoots.read(ENTRYPOINT)).contains("[ -e /app/jagt.yml ] && seed=/app/jagt.yml");
  }

  @Test
  void startCreatesTemporaryAndCacheFoldersPointedUnderTheVolume() {
    assertThat(SourceRoots.read(ENTRYPOINT))
        .contains(
            "for dir in \"$TMPDIR\" \"$XDG_CACHE_HOME\"; do",
            "\"$ORCHESTRATOR_DATA\"/?*) mkdir -p \"$dir\" ;;");
  }

  @Test
  void baseImageInstallsEveryBinaryJagtSpawns() {
    assertThat(SourceRoots.read(Path.of("../base/Dockerfile")))
        .contains("install -y --no-install-recommends git curl tmux ");
  }

  @Test
  void standAgentStartsPastItsFirstRunOnboarding() {
    assertThat(SourceRoots.read(Path.of("../base/Dockerfile.stand")))
        .contains(
            "JAGT_AGENT_SEED=/opt/agent-seed JAGT_AGENT_HOME=/data/home",
            "\"hasCompletedOnboarding\":true",
            "> /opt/agent-seed/.claude.json");
  }
}
