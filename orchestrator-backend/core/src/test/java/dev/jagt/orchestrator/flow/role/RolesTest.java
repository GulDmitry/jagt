package dev.jagt.orchestrator.flow.role;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class RolesTest {

  @Test
  void failsLoudlyWhereTheLintItselfBreaksInsteadOfReadingAsRefusal() {
    CheckerRole hollow = mock(CheckerRole.class);

    assertThatThrownBy(() -> Roles.lint(hollow)).isInstanceOf(NullPointerException.class);
  }
}
