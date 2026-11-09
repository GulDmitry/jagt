package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.AcceptanceVerdict.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcceptanceScoreTest {

  private static final String TWO_ROWS =
      """
      case: two-rows
      ticket:
        key: ABC-2
        title: A title
        criteria:
          - {id: C1, text: "first thing"}
          - {id: C2, text: "second thing"}
      deploy: {sha: abc1234, landed: host-job, diff: ["a/B.java: change"]}
      expect:
        rows: {C1: met, C2: not_met}
        report: plain
        ending: not-met
      names: ["header|columns"]
      """;

  @Test
  void aVerdictMatchingEveryExpectationPasses() {
    AcceptanceCase expected = AcceptanceCases.parse(TWO_ROWS);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc1234",
            "",
            List.of(
                new AcceptanceVerdict.Row("C1", Status.MET, "page answered"),
                new AcceptanceVerdict.Row("C2", Status.NOT_MET, "no Header line")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceScore score =
        AcceptanceScore.of(expected, AcceptanceJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.passed()).isTrue();
    assertThat(score.correct()).isEqualTo(2);
  }

  @Test
  void aMetWhereNotMetWasExpectedIsAFalseMetAndFailsTheCase() {
    AcceptanceCase expected = AcceptanceCases.parse(TWO_ROWS);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc1234",
            "",
            List.of(
                new AcceptanceVerdict.Row("C1", Status.MET, "page answered"),
                new AcceptanceVerdict.Row("C2", Status.MET, "header present")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceScore score =
        AcceptanceScore.of(expected, AcceptanceJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.falseMet()).isEqualTo(1);
    assertThat(score.passed()).isFalse();
  }

  @Test
  void aNotMetWhereMetWasExpectedIsAFalseNotMet() {
    AcceptanceCase expected = AcceptanceCases.parse(TWO_ROWS);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc1234",
            "",
            List.of(
                new AcceptanceVerdict.Row("C1", Status.NOT_MET, "columns wrong"),
                new AcceptanceVerdict.Row("C2", Status.NOT_MET, "header missing")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceScore score =
        AcceptanceScore.of(expected, AcceptanceJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.falseNotMet()).isEqualTo(1);
    assertThat(score.expectedMet()).isEqualTo(1);
  }

  @Test
  void aRowTheVerdictNeverWroteCountsAsWrongNotAsFalseMet() {
    AcceptanceCase expected = AcceptanceCases.parse(TWO_ROWS);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc1234",
            "",
            List.of(new AcceptanceVerdict.Row("C1", Status.MET, "page answered")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceScore score =
        AcceptanceScore.of(expected, AcceptanceJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.rows()).isEqualTo(2);
    assertThat(score.correct()).isEqualTo(1);
    assertThat(score.falseMet()).isZero();
  }

  @Test
  void aNameNoEvidenceCitesFailsAnOtherwiseRightVerdict() {
    AcceptanceCase expected = AcceptanceCases.parse(TWO_ROWS);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc1234",
            "",
            List.of(
                new AcceptanceVerdict.Row("C1", Status.MET, "page answered"),
                new AcceptanceVerdict.Row("C2", Status.NOT_MET, "looks wrong")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceScore score =
        AcceptanceScore.of(expected, AcceptanceJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.namesCited()).isFalse();
    assertThat(score.passed()).isFalse();
  }

  @Test
  void aVerdictNamingEveryExpectedFlowOnTheFixtureUrlPassesAndOneMissingFails() {
    AcceptanceCase expected =
        AcceptanceCases.parse(
            TWO_ROWS.replace(
                "  ending: not-met\n",
                "  ending: not-met\n  flows: {ids: [\"AC-1|@AC-1\", \"expired notice\"]}\n"));
    List<AcceptanceVerdict.Row> rows =
        List.of(
            new AcceptanceVerdict.Row("C1", Status.MET, "page answered"),
            new AcceptanceVerdict.Row("C2", Status.NOT_MET, "no Header line"));
    AcceptanceVerdict both =
        new AcceptanceVerdict(
            "abc1234",
            "",
            rows,
            "",
            "",
            new AcceptanceVerdict.Flows(
                List.of("@AC-1 quote shows validUntil", "shows the expired notice"),
                "http://127.0.0.1:1"));
    AcceptanceVerdict one =
        new AcceptanceVerdict(
            "abc1234",
            "",
            rows,
            "",
            "",
            new AcceptanceVerdict.Flows(
                List.of("@AC-1 quote shows validUntil"), "http://127.0.0.1:1"));

    AcceptanceScore named =
        AcceptanceScore.of(
            expected, AcceptanceJudgement.ofVerdict(expected, both), "http://127.0.0.1:1");
    AcceptanceScore missing =
        AcceptanceScore.of(
            expected, AcceptanceJudgement.ofVerdict(expected, one), "http://127.0.0.1:1");

    assertThat(named.flowsNamed()).isTrue();
    assertThat(missing.flowsNamed()).isFalse();
    assertThat(missing.passed()).isFalse();
  }

  @Test
  void aWaitingVerdictIsReportedAsWaitWithTheProbeItNamed() {
    AcceptanceCase expected = AcceptanceCases.parse(TWO_ROWS);
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc1234", "", List.of(), "", "GET /health", AcceptanceVerdict.Flows.none());

    AcceptanceJudgement judged = AcceptanceJudgement.ofVerdict(expected, verdict);

    assertThat(judged.report()).isEqualTo("wait");
    assertThat(judged.ending()).isEqualTo(AcceptanceEnding.WAITING);
  }

  @Test
  void aDeclineWithoutADisputeAcceptsTheReasonAsMet() {
    AcceptanceCase expected = AcceptanceCases.parse(TWO_ROWS);

    AcceptanceJudgement judged =
        AcceptanceJudgement.ofDecline(
            expected, new dev.jagt.orchestrator.task.AcceptanceDecline("abc1234", "", ""));

    assertThat(judged.ending()).isEqualTo(AcceptanceEnding.DECLINED);
    assertThat(judged.rows()).containsEntry("C1", Status.MET);
  }
}
