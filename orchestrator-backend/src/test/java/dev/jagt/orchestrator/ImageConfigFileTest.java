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
  void upgradeSeedsVolumeFromConfigurationMountedWhereOldImageReadIt() {
    assertThat(SourceRoots.read(ENTRYPOINT)).contains("[ -e /app/jagt.yml ] && seed=/app/jagt.yml");
  }
}
