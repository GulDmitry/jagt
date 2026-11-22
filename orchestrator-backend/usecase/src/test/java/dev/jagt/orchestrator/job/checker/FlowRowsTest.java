package dev.jagt.orchestrator.job.checker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.jagt.orchestrator.job.checker.FlowRows.Decided;
import dev.jagt.orchestrator.job.checker.FlowRows.Outcome;
import dev.jagt.orchestrator.port.worktree.FlowRunner.FlowResult;
import dev.jagt.orchestrator.port.worktree.FlowRunner.Verdict;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Flows;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Flows.Flow;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Row;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Status;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FlowRowsTest {

  static Stream<Arguments> decidesOneRowPerCriterion() {
    return Stream.of(
        arguments(
            "no flow run leaves the read as it was",
            List.of(new Row("C1", Status.MET, "GET 200")),
            new Flows(List.of(), ""),
            List.of(),
            List.of(new Decided(new Row("C1", Status.MET, "GET 200"), Outcome.UNCHANGED))),
        arguments(
            "a passed flow makes its criterion's unprovable row met",
            List.of(new Row("C1", Status.UNPROVABLE, "behind a login")),
            new Flows(List.of(new Flow("open-page", "C1")), ""),
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)),
            List.of(
                new Decided(
                    new Row("C1", Status.MET, "flow open-page passed"), Outcome.MET_BY_FLOW))),
        arguments(
            "a failed flow makes its criterion's met row not met",
            List.of(new Row("C1", Status.MET, "the page loads")),
            new Flows(List.of(new Flow("open-page", "C1")), ""),
            List.of(
                FlowResult.defaults()
                    .withFlow("open-page")
                    .withVerdict(Verdict.FAILED)
                    .withReason("no button")),
            List.of(
                new Decided(
                    new Row("C1", Status.NOT_MET, "flow open-page failed: no button"),
                    Outcome.NOT_MET_BY_FLOW))),
        arguments(
            "an unprovable flow keeps the row's status and adds its reason",
            List.of(new Row("C1", Status.UNPROVABLE, "behind a login")),
            new Flows(List.of(new Flow("open-page", "C1")), ""),
            List.of(
                FlowResult.defaults()
                    .withFlow("open-page")
                    .withVerdict(Verdict.UNPROVABLE)
                    .withReason("no JSON report")),
            List.of(
                new Decided(
                    new Row(
                        "C1",
                        Status.UNPROVABLE,
                        "behind a login | flow open-page unprovable: no JSON report"),
                    Outcome.EVIDENCE_ADDED))),
        arguments(
            "a failed flow outranks a passed one on the same criterion",
            List.of(new Row("C1", Status.UNPROVABLE, "login")),
            new Flows(List.of(new Flow("page-a", "C1"), new Flow("page-b", "C1")), ""),
            List.of(
                FlowResult.defaults().withFlow("page-b").withVerdict(Verdict.FAILED),
                FlowResult.defaults().withFlow("page-a").withVerdict(Verdict.PASSED)),
            List.of(
                new Decided(
                    new Row("C1", Status.NOT_MET, "flow page-b failed"), Outcome.NOT_MET_BY_FLOW))),
        arguments(
            "each flow decides only the row of the criterion it names",
            List.of(
                new Row("C1", Status.UNPROVABLE, "login"),
                new Row("C2", Status.UNPROVABLE, "login")),
            new Flows(List.of(new Flow("page-a", "C1"), new Flow("page-b", "C2")), ""),
            List.of(
                FlowResult.defaults().withFlow("page-a").withVerdict(Verdict.PASSED),
                FlowResult.defaults().withFlow("page-b").withVerdict(Verdict.FAILED)),
            List.of(
                new Decided(new Row("C1", Status.MET, "flow page-a passed"), Outcome.MET_BY_FLOW),
                new Decided(
                    new Row("C2", Status.NOT_MET, "flow page-b failed"), Outcome.NOT_MET_BY_FLOW))),
        arguments(
            "a flow naming no criterion is a met row of its own when it passed",
            List.of(),
            new Flows(List.of(new Flow("AC-1", "")), ""),
            List.of(FlowResult.defaults().withFlow("AC-1").withVerdict(Verdict.PASSED)),
            List.of(new Decided(new Row("AC-1", Status.MET, ""), Outcome.OWN_MET))),
        arguments(
            "a flow naming no criterion is a not met row of its own when it failed",
            List.of(),
            new Flows(List.of(new Flow("AC-1", "")), ""),
            List.of(
                FlowResult.defaults()
                    .withFlow("AC-1")
                    .withVerdict(Verdict.FAILED)
                    .withReason("button missing")),
            List.of(
                new Decided(
                    new Row("AC-1", Status.NOT_MET, "button missing"), Outcome.OWN_NOT_MET))),
        arguments(
            "a flow naming no criterion is an unprovable row of its own when it could not run",
            List.of(),
            new Flows(List.of(new Flow("AC-1", "")), ""),
            List.of(FlowResult.defaults().withFlow("AC-1")),
            List.of(new Decided(new Row("AC-1", Status.UNPROVABLE, ""), Outcome.OWN_UNPROVABLE))),
        arguments(
            "a flow naming a criterion no row has keeps its own row beside the read's",
            List.of(new Row("C1", Status.MET, "ok")),
            new Flows(List.of(new Flow("open-page", "C9")), ""),
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)),
            List.of(
                new Decided(new Row("C1", Status.MET, "ok"), Outcome.UNCHANGED),
                new Decided(new Row("open-page", Status.MET, ""), Outcome.OWN_MET))),
        arguments(
            "a row whose text mentions the flow's criterion is the row it decides",
            List.of(new Row("C1: the page opens", Status.UNPROVABLE, "login")),
            new Flows(List.of(new Flow("open-page", "c1")), ""),
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)),
            List.of(
                new Decided(
                    new Row("C1: the page opens", Status.MET, "flow open-page passed"),
                    Outcome.MET_BY_FLOW))),
        arguments(
            "a row with a blank criterion is never decided by a flow",
            List.of(new Row("", Status.UNPROVABLE, "login")),
            new Flows(List.of(new Flow("open-page", "C1")), ""),
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)),
            List.of(
                new Decided(new Row("", Status.UNPROVABLE, "login"), Outcome.UNCHANGED),
                new Decided(new Row("open-page", Status.MET, ""), Outcome.OWN_MET))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  void decidesOneRowPerCriterion(
      String scenario, List<Row> read, Flows ask, List<FlowResult> run, List<Decided> expected) {
    List<Decided> decided = FlowRows.decide(read, ask, run);

    assertThat(decided).isEqualTo(expected);
  }

  static Stream<Arguments> asksNothingMoreOnceFlowsDecided() {
    return Stream.of(
        arguments(
            "a flow met the only unprovable row",
            List.of(new Row("C1", Status.UNPROVABLE, "login")),
            new Flows(List.of(new Flow("open-page", "C1")), ""),
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)),
            ""),
        arguments(
            "a flow left another row unprovable",
            List.of(
                new Row("C1", Status.UNPROVABLE, "login"),
                new Row("C2", Status.UNPROVABLE, "login")),
            new Flows(List.of(new Flow("open-page", "C1")), ""),
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)),
            "How do I sign in?"),
        arguments(
            "a flow named no criterion of the read",
            List.of(new Row("C1", Status.UNPROVABLE, "login")),
            new Flows(List.of(new Flow("open-page", "C9")), ""),
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)),
            "How do I sign in?"),
        arguments(
            "no flow ran and every row is met",
            List.of(new Row("C1", Status.MET, "GET 200")),
            new Flows(List.of(), ""),
            List.of(),
            "How do I sign in?"),
        arguments(
            "a passed flow of a criterion no row has beside a met row",
            List.of(new Row("C1", Status.MET, "GET 200")),
            new Flows(List.of(new Flow("open-page", "C9")), ""),
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)),
            "How do I sign in?"),
        arguments(
            "no flow ran",
            List.of(new Row("C1", Status.UNPROVABLE, "login")),
            new Flows(List.of(), ""),
            List.of(),
            "How do I sign in?"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  void asksNothingMoreOnceFlowsDecided(
      String scenario, List<Row> read, Flows ask, List<FlowResult> run, String question) {
    AcceptanceVerdict verdict =
        new AcceptanceVerdict("abc", "", read, "How do I sign in?", "", Flows.none());

    AcceptanceVerdict merged = FlowRows.merge(verdict, ask, run);

    assertThat(merged.question()).isEqualTo(question);
  }
}
