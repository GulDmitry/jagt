package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.surface.ui.log.LogFileRoll;
import dev.jagt.orchestrator.surface.ui.log.PortGuard;
import org.junit.jupiter.api.Test;

class LaunchListenersTest {

  @Test
  void launchesWithTheLogFileRollListenerRegistered() {
    assertThat(OrchestratorApplication.application().getListeners())
        .hasAtLeastOneElementOfType(LogFileRoll.class);
  }

  @Test
  void launchesWithThePortGuardListenerRegistered() {
    assertThat(OrchestratorApplication.application().getListeners())
        .hasAtLeastOneElementOfType(PortGuard.class);
  }
}
