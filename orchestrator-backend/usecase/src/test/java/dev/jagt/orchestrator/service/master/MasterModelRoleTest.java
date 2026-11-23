package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import java.util.List;
import org.junit.jupiter.api.Test;

class MasterModelRoleTest {

  private final InstalledRoles installed = mock(InstalledRoles.class);
  private final ConfigService config = mock(ConfigService.class);

  @Test
  void runsWhereItsModeIsNotOff() {
    when(installed.readers()).thenReturn(List.of("master"));
    when(config.load())
        .thenReturn(
            ConfigFile.defaults().withArbiter(new ArbiterConfig("judge", null, null, null, null)));

    assertThat(new MasterModelRole(installed, config).running()).isTrue();
  }

  @Test
  void doesNotRunWhereItsModeIsOff() {
    when(installed.readers()).thenReturn(List.of("master"));
    when(config.load())
        .thenReturn(
            ConfigFile.defaults().withArbiter(new ArbiterConfig("off", null, null, null, null)));

    assertThat(new MasterModelRole(installed, config).running()).isFalse();
  }
}
