package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.AcceptanceDecline;
import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.AcceptanceVerdict.Status;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record AcceptanceJudgement(
    Map<String, Status> rows,
    AcceptanceEnding ending,
    String report,
    String cited,
    AcceptanceVerdict.Flows flows) {

  public static AcceptanceJudgement ofVerdict(AcceptanceCase expected, AcceptanceVerdict verdict) {
    Map<String, Status> rows = new LinkedHashMap<>();
    for (AcceptanceCase.Criterion criterion : expected.criteria()) {
      verdict.rows().stream()
          .filter(row -> answers(row.criterion(), criterion))
          .findFirst()
          .ifPresent(row -> rows.put(criterion.id(), row.status()));
    }
    String cited =
        verdict.rows().stream()
                .map(AcceptanceVerdict.Row::evidence)
                .collect(Collectors.joining("\n"))
            + "\n"
            + verdict.question()
            + "\n"
            + verdict.waitingOn()
            + "\n"
            + verdict.failure();
    return new AcceptanceJudgement(
        rows, verdict.ending(), reportOf(verdict), cited.toLowerCase(Locale.ROOT), verdict.flows());
  }

  public static AcceptanceJudgement ofDecline(AcceptanceCase expected, AcceptanceDecline decline) {
    boolean failed = !decline.failure().isBlank();
    boolean disputed = !decline.dispute().isBlank();
    Status accepted = disputed ? Status.NOT_MET : Status.MET;
    Map<String, Status> rows = new LinkedHashMap<>();
    if (!failed) {
      expected.criteria().forEach(criterion -> rows.put(criterion.id(), accepted));
    }
    AcceptanceEnding ending =
        failed
            ? AcceptanceEnding.FAILED
            : disputed ? AcceptanceEnding.DISPUTED : AcceptanceEnding.DECLINED;
    String report = failed ? "failed" : disputed ? "dispute" : "plain";
    return new AcceptanceJudgement(
        rows,
        ending,
        report,
        (decline.dispute() + "\n" + decline.failure()).toLowerCase(Locale.ROOT),
        AcceptanceVerdict.Flows.none());
  }

  private static String reportOf(AcceptanceVerdict verdict) {
    if (!verdict.failure().isBlank()) {
      return "failed";
    }
    if (!verdict.waitingOn().isBlank()) {
      return "wait";
    }
    return verdict.question().isBlank() ? "plain" : "question";
  }

  private static boolean answers(String written, AcceptanceCase.Criterion criterion) {
    return Pattern.compile("(?i)(^|\\W)" + Pattern.quote(criterion.id()) + "(\\W|$)")
            .matcher(written)
            .find()
        || written.toLowerCase(Locale.ROOT).contains(criterion.text().toLowerCase(Locale.ROOT));
  }
}
