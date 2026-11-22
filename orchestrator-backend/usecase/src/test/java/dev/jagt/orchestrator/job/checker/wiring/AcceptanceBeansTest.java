package dev.jagt.orchestrator.job.checker.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.RoleKit;
import dev.jagt.orchestrator.service.role.RoleKits;
import dev.jagt.orchestrator.service.role.RoleSettings;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceBeansTest {

  @Test
  void acceptanceFilesAndSettingsAreThoseOfTheAcceptanceKit(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    RoleDefinition absent = RoleDefinition.absent(Checkers.ACCEPTANCE);
    RoleKits kits =
        new RoleKits(
            List.of(
                new RoleKit(
                    new RoleSettings(absent, mock(ConfigService.class), paths),
                    new RoleFiles(paths, Checkers.ACCEPTANCE))));

    assertThat(new AcceptanceBeans().acceptanceFiles(kits))
        .isSameAs(kits.find(Checkers.ACCEPTANCE).orElseThrow().files());
    assertThat(new AcceptanceBeans().acceptanceSettings(kits))
        .isSameAs(kits.find(Checkers.ACCEPTANCE).orElseThrow().settings());
  }
}
