package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceVerdict.Status;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class AcceptanceCasesTest {

  @Test
  void everyShippedCaseLoadsWithAnExpectedRowPerCriterion() {
    var cases = RoleCases.in(AcceptanceCases::parse, Path.of("..", "roles", "acceptance", "eval"));

    assertThat(cases)
        .hasSize(17)
        .allSatisfy(loaded ->
            assertThat(loaded.expect().rows().keySet())
                .containsExactlyInAnyOrderElementsOf(
                    loaded.ticket().criteria().stream()
                        .map(AcceptanceCase.Criterion::id)
                        .toList()));
  }

  @Test
  void caseKeepsWhatItExpectsOfTheVerdict() {
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

    assertThat(loaded.expect().rows()).containsEntry("C2", Status.NOT_MET);
    assertThat(loaded.ticket().criteria().get(1).testable()).isFalse();
    assertThat(loaded.landing().deploy().sha()).isEqualTo("1829304");
    assertThat(loaded.expectedEnding()).isEqualTo(AcceptanceEnding.NOT_MET);
    assertThat(loaded.expect().probe()).isEqualTo("GET /version");
    assertThat(loaded.declines()).isFalse();
  }

  @Test
  void caseWithSessionReplyIsDeclineCase() {
    AcceptanceCase loaded =
        AcceptanceCases.all().stream().filter(AcceptanceCase::declines).findFirst().orElseThrow();

    assertThat(Objects.requireNonNull(loaded.decline()).why()).contains("pinned");
  }

  @Test
  void caseKeepsTheWorkspaceItSeedsAndWhatItExpectsRunToWrite() {
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
  void caseKeepsTheFlowsDirectoryAndCommandItPointsAt() {
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
