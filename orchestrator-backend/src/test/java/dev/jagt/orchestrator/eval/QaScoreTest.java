package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.QaVerdict.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class QaScoreTest {

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
    QaCase expected = QaCases.parse(TWO_ROWS);
    QaVerdict verdict =
        new QaVerdict(
            "abc1234",
            "",
            List.of(
                new QaVerdict.Row("C1", Status.MET, "page answered"),
                new QaVerdict.Row("C2", Status.NOT_MET, "no Header line")),
            "",
            "",
            QaVerdict.Flows.none());

    QaScore score = QaScore.of(expected, QaJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.passed()).isTrue();
    assertThat(score.correct()).isEqualTo(2);
  }

  @Test
  void aMetWhereNotMetWasExpectedIsAFalseMetAndFailsTheCase() {
    QaCase expected = QaCases.parse(TWO_ROWS);
    QaVerdict verdict =
        new QaVerdict(
            "abc1234",
            "",
            List.of(
                new QaVerdict.Row("C1", Status.MET, "page answered"),
                new QaVerdict.Row("C2", Status.MET, "header present")),
            "",
            "",
            QaVerdict.Flows.none());

    QaScore score = QaScore.of(expected, QaJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.falseMet()).isEqualTo(1);
    assertThat(score.passed()).isFalse();
  }

  @Test
  void aNotMetWhereMetWasExpectedIsAFalseNotMet() {
    QaCase expected = QaCases.parse(TWO_ROWS);
    QaVerdict verdict =
        new QaVerdict(
            "abc1234",
            "",
            List.of(
                new QaVerdict.Row("C1", Status.NOT_MET, "columns wrong"),
                new QaVerdict.Row("C2", Status.NOT_MET, "header missing")),
            "",
            "",
            QaVerdict.Flows.none());

    QaScore score = QaScore.of(expected, QaJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.falseNotMet()).isEqualTo(1);
    assertThat(score.expectedMet()).isEqualTo(1);
  }

  @Test
  void aRowTheVerdictNeverWroteCountsAsWrongNotAsFalseMet() {
    QaCase expected = QaCases.parse(TWO_ROWS);
    QaVerdict verdict =
        new QaVerdict(
            "abc1234", "", List.of(new QaVerdict.Row("C1", Status.MET, "page answered")), "", "",
            QaVerdict.Flows.none());

    QaScore score = QaScore.of(expected, QaJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.rows()).isEqualTo(2);
    assertThat(score.correct()).isEqualTo(1);
    assertThat(score.falseMet()).isZero();
  }

  @Test
  void aNameNoEvidenceCitesFailsAnOtherwiseRightVerdict() {
    QaCase expected = QaCases.parse(TWO_ROWS);
    QaVerdict verdict =
        new QaVerdict(
            "abc1234",
            "",
            List.of(
                new QaVerdict.Row("C1", Status.MET, "page answered"),
                new QaVerdict.Row("C2", Status.NOT_MET, "looks wrong")),
            "",
            "",
            QaVerdict.Flows.none());

    QaScore score = QaScore.of(expected, QaJudgement.ofVerdict(expected, verdict), "");

    assertThat(score.namesCited()).isFalse();
    assertThat(score.passed()).isFalse();
  }

  @Test
  void aVerdictNamingEveryExpectedFlowOnTheFixtureUrlPassesAndOneMissingFails() {
    QaCase expected =
        QaCases.parse(
            TWO_ROWS.replace(
                "  ending: not-met\n",
                "  ending: not-met\n  flows: {ids: [\"AC-1|@AC-1\", \"expired notice\"]}\n"));
    List<QaVerdict.Row> rows =
        List.of(
            new QaVerdict.Row("C1", Status.MET, "page answered"),
            new QaVerdict.Row("C2", Status.NOT_MET, "no Header line"));
    QaVerdict both =
        new QaVerdict(
            "abc1234",
            "",
            rows,
            "",
            "",
            new QaVerdict.Flows(
                List.of("@AC-1 quote shows validUntil", "shows the expired notice"),
                "http://127.0.0.1:1"));
    QaVerdict one =
        new QaVerdict(
            "abc1234",
            "",
            rows,
            "",
            "",
            new QaVerdict.Flows(List.of("@AC-1 quote shows validUntil"), "http://127.0.0.1:1"));

    QaScore named = QaScore.of(expected, QaJudgement.ofVerdict(expected, both), "http://127.0.0.1:1");
    QaScore missing = QaScore.of(expected, QaJudgement.ofVerdict(expected, one), "http://127.0.0.1:1");

    assertThat(named.flowsNamed()).isTrue();
    assertThat(missing.flowsNamed()).isFalse();
    assertThat(missing.passed()).isFalse();
  }

  @Test
  void aWaitingVerdictIsReportedAsWaitWithTheProbeItNamed() {
    QaCase expected = QaCases.parse(TWO_ROWS);
    QaVerdict verdict = new QaVerdict("abc1234", "", List.of(), "", "GET /health", QaVerdict.Flows.none());

    QaJudgement judged = QaJudgement.ofVerdict(expected, verdict);

    assertThat(judged.report()).isEqualTo("wait");
    assertThat(judged.ending()).isEqualTo(QaEnding.WAITING);
  }

  @Test
  void aDeclineWithoutADisputeAcceptsTheReasonAsMet() {
    QaCase expected = QaCases.parse(TWO_ROWS);

    QaJudgement judged =
        QaJudgement.ofDecline(
            expected, new dev.jagt.orchestrator.task.QaDecline("abc1234", "", ""));

    assertThat(judged.ending()).isEqualTo(QaEnding.DECLINED);
    assertThat(judged.rows()).containsEntry("C1", Status.MET);
  }
}
