package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.host.SystemHost;
import org.junit.jupiter.api.Test;

class OrchestratorLaunchTest {

  @Test
  void passesNamedConfigFileOnceSoSpringDoesNotJoinItWithItself() {
    String[] launched =
        OrchestratorApplication.withConfigFile(
            new String[] {"--orchestrator.config-file=/opt/jagt.yml"}, new SystemHost());

    assertThat(launched)
        .filteredOn(arg -> arg.startsWith("--orchestrator.config-file="))
        .containsExactly("--orchestrator.config-file=/opt/jagt.yml");
  }

  @Test
  void pinsTheDefaultDataFolderOutsideTheNamedRoot() {
    String[] launched =
        OrchestratorApplication.withConfigFile(
            new String[] {"--orchestrator.root=/opt/jagt"}, new SystemHost());

    assertThat(launched)
        .contains("--orchestrator.default-data=" + new SystemHost().home().resolve(".jagt/data"));
  }
}
