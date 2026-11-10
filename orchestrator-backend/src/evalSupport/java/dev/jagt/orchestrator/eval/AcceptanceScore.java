package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.AcceptanceVerdict.Status;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import org.jspecify.annotations.Nullable;

public record AcceptanceScore(
    Rows rows,
    boolean reportMatches,
    boolean endingMatches,
    boolean namesCited,
    boolean flowsNamed) {

  public record Rows(
      int total,
      int correct,
      int expectedMet,
      int falseNotMet,
      int falseMet,
      int judgedUnprovable,
      int expectedUnprovable) {}

  public static AcceptanceScore of(
      AcceptanceCase expected, AcceptanceJudgement judged, String environmentUrl) {
    Map<String, Status> want = expected.expect().rows();
    Map<String, Status> got = judged.rows();
    int correct = tally(want, got, (wanted, judgedAs) -> wanted == judgedAs);
    int expectedMet = tally(want, got, (wanted, judgedAs) -> wanted == Status.MET);
    int expectedUnprovable = tally(want, got, (wanted, judgedAs) -> wanted == Status.UNPROVABLE);
    int judgedUnprovable = tally(want, got, (wanted, judgedAs) -> judgedAs == Status.UNPROVABLE);
    int falseNotMet =
        tally(want, got, (wanted, judgedAs) -> wanted == Status.MET && judgedAs == Status.NOT_MET);
    int falseMet =
        tally(want, got, (wanted, judgedAs) -> wanted != Status.MET && judgedAs == Status.MET);
    return new AcceptanceScore(
        new Rows(
            expected.expect().rows().size(),
            correct,
            expectedMet,
            falseNotMet,
            falseMet,
            judgedUnprovable,
            expectedUnprovable),
        expected.expect().report().equals(judged.report()),
        expected.expectedEnding() == judged.ending(),
        cited(expected, judged),
        flowsNamed(expected, judged, environmentUrl));
  }

  private static int tally(
      Map<String, Status> want,
      Map<String, Status> got,
      BiPredicate<Status, @Nullable Status> counts) {
    return (int)
        want.entrySet().stream()
            .filter(row -> counts.test(row.getValue(), got.get(row.getKey())))
            .count();
  }

  private static boolean flowsNamed(
      AcceptanceCase expected, AcceptanceJudgement judged, String environmentUrl) {
    if (!expected.hasFlows()) {
      return true;
    }
    String named = String.join("\n", judged.flows().ids()).toLowerCase(Locale.ROOT);
    return expected.flows().ids().stream().allMatch(alternatives -> anyOf(alternatives, named))
        && environmentUrl.equals(judged.flows().environment());
  }

  private static boolean cited(AcceptanceCase expected, AcceptanceJudgement judged) {
    boolean namesCited =
        expected.expect().names().stream().allMatch(name -> anyOf(name, judged.cited()));
    boolean probeNamed =
        expected.expect().probe().isEmpty()
            || judged
                .cited()
                .contains(
                    expected
                        .expect()
                        .probe()
                        .substring(expected.expect().probe().indexOf(' ') + 1)
                        .toLowerCase(Locale.ROOT));
    return namesCited && probeNamed;
  }

  private static boolean anyOf(String alternatives, String cited) {
    for (String alternative : alternatives.split("\\|", 0)) {
      if (cited.contains(alternative.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  public boolean passed() {
    return rows.falseMet() == 0
        && rows.correct() == rows.total()
        && reportMatches
        && endingMatches
        && namesCited
        && flowsNamed;
  }

  public String describe() {
    return "rows "
        + rows.correct()
        + "/"
        + rows.total()
        + ", false met "
        + rows.falseMet()
        + ", false not met "
        + rows.falseNotMet()
        + ", report "
        + reportMatches
        + ", ending "
        + endingMatches
        + ", names cited "
        + namesCited
        + ", flows named "
        + flowsNamed;
  }
}
