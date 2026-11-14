package dev.jagt.orchestrator.service.reach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.role.RoleOptions;
import dev.jagt.orchestrator.service.role.RoleSettings;
import org.junit.jupiter.api.Test;

class ScenarioReadinessTest {

  private final RoleSettings settings = mock(RoleSettings.class);
  private final ScenarioReadiness readiness = new ScenarioReadiness(settings);

  @Test
  void isReadyWhereTheRoleIsOnAndHoldsWorkspace() {
    when(settings.load()).thenReturn(RoleOptions.defaults().withEnabled(true).withWorkspace("/ws"));

    assertThat(readiness.ready()).isTrue();
  }

  @Test
  void isNotReadyWhereTheRoleIsOff() {
    when(settings.load())
        .thenReturn(RoleOptions.defaults().withEnabled(false).withWorkspace("/ws"));

    assertThat(readiness.ready()).isFalse();
  }

  @Test
  void isNotReadyWhereTheRoleHoldsNoWorkspace() {
    when(settings.load()).thenReturn(RoleOptions.defaults().withEnabled(true).withWorkspace(""));

    assertThat(readiness.ready()).isFalse();
  }
}
