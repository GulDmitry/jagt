package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceVerdict.Status;
import org.junit.jupiter.api.Test;

class AcceptanceCasesTest {

  @Test
  void everyShippedCaseLoadsWithAnExpectedRowPerCriterion() {
    var cases = AcceptanceCases.all();

    assertThat(cases)
        .hasSizeGreaterThanOrEqualTo(17)
        .allSatisfy(
            loaded ->
                assertThat(loaded.rows().keySet())
                    .containsExactlyInAnyOrderElementsOf(
                        loaded.criteria().stream().map(AcceptanceCase.Criterion::id).toList()));
  }

  @Test
  void aCaseKeepsWhatItExpectsOfTheVerdict() {
    AcceptanceCase loaded =
        AcceptanceCases.parse(
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
    assertThat(loaded.expectedEnding()).isEqualTo(AcceptanceEnding.NOT_MET);
    assertThat(loaded.probe()).isEqualTo("GET /version");
    assertThat(loaded.declines()).isFalse();
  }

  @Test
  void aCaseWithASessionReplyIsADeclineCase() {
    AcceptanceCase loaded =
        AcceptanceCases.all().stream().filter(AcceptanceCase::declines).findFirst().orElseThrow();

    assertThat(loaded.decline().why()).contains("pinned");
  }

  @Test
  void aCaseKeepsTheWorkspaceItSeedsAndWhatItExpectsRunToWrite() {
    AcceptanceCase loaded =
        AcceptanceCases.parse(
            """
            case: seeds
            ticket:
              key: ABC-1
              title: A title
              criteria:
                - {id: C1, text: "first"}
            deploy: {sha: 1829304, landed: host-job, diff: ["a/B.java: change"]}
            workspace:
              index.md: "# index"
            expect:
              rows: {C1: met}
              report: plain
              ending: passed
              writes: new
            names: []
            """);

    assertThat(loaded.workspace().held()).isTrue();
    assertThat(loaded.workspace().files()).containsEntry("index.md", "# index");
    assertThat(loaded.workspace().writes()).isEqualTo("new");
  }

  @Test
  void aCaseKeepsTheFlowsDirectoryAndCommandItPointsAt() {
    AcceptanceCase loaded =
        AcceptanceCases.parse(
            """
            case: points
            ticket:
              key: ABC-1
              title: A title
              criteria:
                - {id: C1, text: "first"}
            deploy: {sha: 1829304, landed: host-job, diff: ["a/B.java: change"]}
            environment:
              live: https://app.example.test
            flows:
              path: /checks/example
              command: ["npx", "playwright", "test"]
            expect:
              rows: {C1: met}
              report: plain
              ending: passed
            names: []
            """);

    assertThat(loaded.flows().path()).isEqualTo("/checks/example");
    assertThat(loaded.flows().command()).containsExactly("npx", "playwright", "test");
    assertThat(loaded.flows().files()).isEmpty();
  }
}
