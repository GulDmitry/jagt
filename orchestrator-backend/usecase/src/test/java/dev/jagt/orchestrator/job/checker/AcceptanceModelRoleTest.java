package dev.jagt.orchestrator.job.checker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AcceptanceModelRoleTest {

  private final InstalledRoles installed = mock(InstalledRoles.class);
  private final ConfigService config = mock(ConfigService.class);

  @Test
  void runsWhereItIsInstalledAndEnabled() {
    when(installed.find("acceptance")).thenReturn(Optional.of(mock(RoleDefinition.class)));
    when(config.load())
        .thenReturn(
            ConfigFile.defaults().withRoles(Map.of("acceptance", new RoleConfig(true, null))));

    assertThat(new AcceptanceModelRole(installed, config).running()).isTrue();
  }

  @Test
  void doesNotRunWhereItIsDisabled() {
    when(installed.find("acceptance")).thenReturn(Optional.of(mock(RoleDefinition.class)));
    when(config.load()).thenReturn(ConfigFile.defaults());

    assertThat(new AcceptanceModelRole(installed, config).running()).isFalse();
  }
}
