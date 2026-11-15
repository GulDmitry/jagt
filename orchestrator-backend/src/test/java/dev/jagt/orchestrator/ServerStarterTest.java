package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ServerStarterTest {

  private static final Path SERVER = Path.of("../jagt.server.yml.dist");
  private static final Path DESKTOP = Path.of("../jagt.desktop.yml.dist");

  @Test
  void everyKeyOfTheServerStarterIsDescribedInTheDesktopReferenceAlone() {
    String server = SourceRoots.read(SERVER);
    String desktop = SourceRoots.read(DESKTOP);

    assertThat(
            Pattern.compile("(?m)^\\s*([a-zA-Z]+):")
                .matcher(server)
                .results()
                .map(match -> match.group(1))
                .distinct()
                .filter(
                    key -> !Pattern.compile("(?m)^\\s*#?\\s*" + key + ":").matcher(desktop).find()))
        .isEmpty();
  }

  @Test
  void theServerStarterDescribesNoKeyInProse() {
    String server = SourceRoots.read(SERVER);

    assertThat(Pattern.compile("(?m)^\\s*#.{100,}$").matcher(server).find()).isFalse();
  }

  @Test
  void theServerStarterEndsSessionsOnStopAndLogsToTheConsole() {
    String server = SourceRoots.read(SERVER);

    assertThat(server).containsPattern("(?m)^  onStop: end\\b");
    assertThat(server).containsPattern("(?m)^  logging: console\\b");
  }
}
