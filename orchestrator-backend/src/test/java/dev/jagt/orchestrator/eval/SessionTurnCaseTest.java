package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionTurnCaseTest {

  @Test
  void everyShippedShipCaseExpectsCiPolling() {
    var cases = SessionTurnCase.all();

    assertThat(cases)
        .filteredOn(turn -> turn.step().equals("ship"))
        .isNotEmpty()
        .allSatisfy(turn -> assertThat(turn.status()).isEqualTo("CI_POLLING"));
  }
}
