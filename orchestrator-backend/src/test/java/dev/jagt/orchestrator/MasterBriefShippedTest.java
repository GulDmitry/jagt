package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MasterBriefShippedTest {

  private static final Path SHIPPED = Path.of("../roles/master/brief.md.dist");

  @Test
  void shipsTheBriefUnderTheNameTheRefusalTellsAHumanToCopy() {
    assertThat(Files.isRegularFile(SHIPPED)).describedAs("%s", SHIPPED).isTrue();
    assertThat(Path.of("..").relativize(SHIPPED).toString())
        .isEqualTo(MasterConfig.defaults().briefOrDefault() + ".dist");
  }

  @Test
  void keepsAHumansOwnBriefOutOfTheRepository() throws Exception {
    assertThat(Files.readString(Path.of("../.gitignore")))
        .contains("\nroles/*/*\n", "\n!roles/*/*.dist\n");
  }
}
