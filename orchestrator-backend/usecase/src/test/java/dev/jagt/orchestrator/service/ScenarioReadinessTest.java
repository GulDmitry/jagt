package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.RoleOptions;
import org.junit.jupiter.api.Test;

class ScenarioReadinessTest {

  private final RoleSettings settings = mock(RoleSettings.class);
  private final ScenarioReadiness readiness = new ScenarioReadiness(settings);

  @Test
  void isReadyWhereTheRoleIsOnAndHoldsAWorkspace() {
    when(settings.load())
        .thenReturn(RoleOptions.defaults().withEnabled(true).withWorkspace("/ws"));

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
