package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.QaVerdict.Status;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record QaJudgement(Map<String, Status> rows, QaEnding ending, String report, String cited) {

  public static QaJudgement ofVerdict(QaCase expected, QaVerdict verdict) {
    Map<String, Status> rows = new LinkedHashMap<>();
    for (QaCase.Criterion criterion : expected.criteria()) {
      verdict.rows().stream()
          .filter(row -> answers(row.criterion(), criterion))
          .findFirst()
          .ifPresent(row -> rows.put(criterion.id(), row.status()));
    }
    String cited =
        verdict.rows().stream().map(QaVerdict.Row::evidence).collect(Collectors.joining("\n"))
            + "\n"
            + verdict.question()
            + "\n"
            + verdict.waitingOn()
            + "\n"
            + verdict.failure();
    return new QaJudgement(
        rows, verdict.ending(), reportOf(verdict), cited.toLowerCase(Locale.ROOT));
  }

  public static QaJudgement ofDecline(QaCase expected, QaDecline decline) {
    boolean failed = !decline.failure().isBlank();
    boolean disputed = !decline.dispute().isBlank();
    Status accepted = disputed ? Status.NOT_MET : Status.MET;
    Map<String, Status> rows = new LinkedHashMap<>();
    if (!failed) {
      expected.criteria().forEach(criterion -> rows.put(criterion.id(), accepted));
    }
    QaEnding ending = failed ? QaEnding.FAILED : disputed ? QaEnding.DISPUTED : QaEnding.DECLINED;
    String report = failed ? "failed" : disputed ? "dispute" : "plain";
    return new QaJudgement(
        rows,
        ending,
        report,
        (decline.dispute() + "\n" + decline.failure()).toLowerCase(Locale.ROOT));
  }

  private static String reportOf(QaVerdict verdict) {
    if (!verdict.failure().isBlank()) {
      return "failed";
    }
    if (!verdict.waitingOn().isBlank()) {
      return "wait";
    }
    return verdict.question().isBlank() ? "plain" : "question";
  }

  private static boolean answers(String written, QaCase.Criterion criterion) {
    return Pattern.compile("(?i)(^|\\W)" + Pattern.quote(criterion.id()) + "(\\W|$)")
            .matcher(written)
            .find()
        || written.toLowerCase(Locale.ROOT).contains(criterion.text().toLowerCase(Locale.ROOT));
  }
}
