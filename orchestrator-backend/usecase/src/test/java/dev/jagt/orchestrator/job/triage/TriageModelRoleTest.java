package dev.jagt.orchestrator.job.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TriageModelRoleTest {

  private final InstalledRoles installed = mock(InstalledRoles.class);
  private final ConfigService config = mock(ConfigService.class);

  @Test
  void runsWhereItIsInstalledAndEnabled() {
    when(installed.readers()).thenReturn(List.of("triage"));
    when(config.load())
        .thenReturn(ConfigFile.defaults().withRoles(Map.of("triage", new RoleConfig(true, null))));

    assertThat(new TriageModelRole(installed, config).running()).isTrue();
  }

  @Test
  void doesNotRunWhereItIsDisabled() {
    when(installed.readers()).thenReturn(List.of("triage"));
    when(config.load()).thenReturn(ConfigFile.defaults());

    assertThat(new TriageModelRole(installed, config).running()).isFalse();
  }
}
