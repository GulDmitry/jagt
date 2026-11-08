package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;

class StopTriageCasesTest {

  @Test
  void everyWhoClassHasOneShippedStop() {
    var cases = StopTriageCases.all();

    assertThat(cases)
        .extracting(StopTriageCase::who)
        .containsExactlyInAnyOrder(StopTriage.Who.values());
  }

  @Test
  void aStopKeepsItsTriggerEvidenceAndExpectedReading() {
    StopTriageCase loaded =
        StopTriageCases.parse(
            """
            case: a loop
            stop:
              trigger: ROUNDS
              evidence: sent 3 times
            trail: |
              10:00 round
            commits: |
              abc1234 Change
            expect:
              who: session
              lift: unsafe
              family: F
            """);

    assertThat(loaded.trigger()).isEqualTo(TaskStop.Trigger.ROUNDS);
    assertThat(loaded.evidence()).isEqualTo("sent 3 times");
    assertThat(loaded.who()).isEqualTo(StopTriage.Who.SESSION);
    assertThat(loaded.lift()).isEqualTo(StopTriage.Lift.UNSAFE);
    assertThat(loaded.family()).isEqualTo("F");
  }
}
