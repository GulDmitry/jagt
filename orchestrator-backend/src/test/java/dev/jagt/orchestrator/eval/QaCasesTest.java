package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.QaVerdict.Status;
import org.junit.jupiter.api.Test;

class QaCasesTest {

  @Test
  void everyShippedCaseLoadsWithAnExpectedRowPerCriterion() {
    var cases = QaCases.all();

    assertThat(cases)
        .hasSize(14)
        .allSatisfy(
            loaded ->
                assertThat(loaded.rows().keySet())
                    .containsExactlyInAnyOrderElementsOf(
                        loaded.criteria().stream().map(QaCase.Criterion::id).toList()));
  }

  @Test
  void aCaseKeepsWhatItExpectsOfTheVerdict() {
    QaCase loaded =
        QaCases.parse(
            """
            case: waits
            ticket:
              key: ABC-1
              title: A title
              criteria:
                - {id: C1, text: "first"}
                - {id: C2, text: "second", testable: false}
            deploy: {sha: 1829304, landed: host-job, diff: ["a/B.java: change"]}
            expect:
              rows: {C1: met, C2: not_met}
              report: wait
              probe: "GET /version"
              ending: not-met
            names: ["one|two"]
            """);

    assertThat(loaded.rows()).containsEntry("C2", Status.NOT_MET);
    assertThat(loaded.criteria().get(1).testable()).isFalse();
    assertThat(loaded.deploy().sha()).isEqualTo("1829304");
    assertThat(loaded.expectedEnding()).isEqualTo(QaEnding.NOT_MET);
    assertThat(loaded.probe()).isEqualTo("GET /version");
    assertThat(loaded.declines()).isFalse();
  }

  @Test
  void aCaseWithASessionReplyIsADeclineCase() {
    QaCase loaded = QaCases.all().stream().filter(QaCase::declines).findFirst().orElseThrow();

    assertThat(loaded.decline().why()).contains("pinned");
  }
}
