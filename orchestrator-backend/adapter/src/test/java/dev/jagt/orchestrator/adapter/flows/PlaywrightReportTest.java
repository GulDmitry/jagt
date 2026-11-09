package dev.jagt.orchestrator.adapter.flows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaywrightReportTest {

  private static final Path REPORT = Path.of("/wt/acceptance/report.json");

  @Test
  void flowPassesWhenEveryTestOfItsSpecWasExpected() {
    String json =
        """
        {"suites":[{"specs":[{"title":"alpha login","tags":["AC-1"],
          "tests":[{"status":"expected","results":[]}]}]}]}
        """;

    List<FlowResult> results = new PlaywrightReport(json).results(List.of("@AC-1"), REPORT);

    assertThat(results)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.verdict()).isEqualTo(Verdict.PASSED);
              assertThat(r.evidence()).containsExactly(REPORT);
            });
  }

  @Test
  void flowFailsWhenATestOfItsSpecWasUnexpectedAndKeepsItsAttachments() {
    String json =
        """
        {"suites":[{"suites":[{"specs":[{"title":"alpha search @AC-2","tags":[],
          "tests":[{"status":"unexpected","results":[
            {"attachments":[{"name":"trace","path":"/wt/acceptance/artifacts/trace.zip"}]}]}]}]}]}]}
        """;

    List<FlowResult> results = new PlaywrightReport(json).results(List.of("AC-2"), REPORT);

    assertThat(results)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.verdict()).isEqualTo(Verdict.FAILED);
              assertThat(r.reason()).isEqualTo("failed: alpha search @AC-2");
              assertThat(r.evidence())
                  .containsExactly(REPORT, Path.of("/wt/acceptance/artifacts/trace.zip"));
            });
  }

  @Test
  void flowIsUnprovableWhenItsTestPassedOnlyOnRetry() {
    String json =
        """
        {"suites":[{"specs":[{"title":"alpha export","tags":["AC-3"],
          "tests":[{"status":"flaky","results":[]}]}]}]}
        """;

    List<FlowResult> results = new PlaywrightReport(json).results(List.of("AC-3"), REPORT);

    assertThat(results)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.verdict()).isEqualTo(Verdict.UNPROVABLE);
              assertThat(r.reason()).isEqualTo("flaky or skipped: alpha export");
            });
  }

  @Test
  void flowIsUnprovableWhenItsTestWasSkipped() {
    String json =
        """
        {"suites":[{"specs":[{"title":"alpha export","tags":["AC-3"],
          "tests":[{"status":"skipped","results":[]}]}]}]}
        """;

    List<FlowResult> results = new PlaywrightReport(json).results(List.of("AC-3"), REPORT);

    assertThat(results.get(0).verdict()).isEqualTo(Verdict.UNPROVABLE);
  }

  @Test
  void flowIsUnprovableWhenNoSpecCarriesIt() {
    String json =
        """
        {"suites":[{"specs":[{"title":"alpha login AC-10","tags":["AC-10"],
          "tests":[{"status":"expected","results":[]}]}]}]}
        """;

    List<FlowResult> results = new PlaywrightReport(json).results(List.of("AC-1"), REPORT);

    assertThat(results)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.verdict()).isEqualTo(Verdict.UNPROVABLE);
              assertThat(r.reason()).isEqualTo("no spec carries this flow");
            });
  }

  @Test
  void flowIsFoundByItsWholeTitle() {
    String json =
        """
        {"suites":[{"specs":[{"title":"alpha login works","tags":[],
          "tests":[{"status":"expected","results":[]}]}]}]}
        """;

    List<FlowResult> results =
        new PlaywrightReport(json).results(List.of("alpha login works"), REPORT);

    assertThat(results.get(0).verdict()).isEqualTo(Verdict.PASSED);
  }

  @Test
  void aFailedSpecDoesNotFailAnotherFlow() {
    String json =
        """
        {"suites":[{"specs":[
          {"title":"a","tags":["AC-1"],"tests":[{"status":"expected","results":[]}]},
          {"title":"b","tags":["AC-2"],"tests":[{"status":"unexpected","results":[]}]}]}]}
        """;

    List<FlowResult> results = new PlaywrightReport(json).results(List.of("AC-1", "AC-2"), REPORT);

    assertThat(results)
        .extracting(FlowResult::verdict)
        .containsExactly(Verdict.PASSED, Verdict.FAILED);
  }
}
