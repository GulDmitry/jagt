package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StopTriageTest {

  @Test
  void theHoverLineNamesWhoTheClassTheFamilyAndTheLift() {
    StopTriage triage =
        new StopTriage(
            StopTriage.Who.JAGT,
            "ships counted past a merge",
            "H",
            StopTriage.Lift.SAFE,
            "request merged at 09:20",
            "");

    assertThat(triage.line())
        .isEqualTo("triage reads: jagt — ships counted past a merge (H), lift looks safe");
  }
}
