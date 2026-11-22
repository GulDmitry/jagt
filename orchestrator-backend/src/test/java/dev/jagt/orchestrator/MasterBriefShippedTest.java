package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile.ArbiterConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MasterBriefShippedTest {

  private static final Path SHIPPED = Path.of("../roles/master/brief.md.dist");

  @Test
  void shipsTheBriefUnderTheNameTheRefusalTellsHumanToCopy() {
    assertThat(Files.isRegularFile(SHIPPED)).describedAs("%s", SHIPPED).isTrue();
    assertThat(Path.of("..").relativize(SHIPPED).toString())
        .isEqualTo(ArbiterConfig.defaults().briefOrDefault() + ".dist");
  }

  @Test
  void keepsHumansOwnBriefOutOfTheRepository() throws Exception {
    assertThat(Files.readString(Path.of("../.gitignore")))
        .contains("\nroles/*/*\n", "\n!roles/*/*.dist\n");
  }
}
