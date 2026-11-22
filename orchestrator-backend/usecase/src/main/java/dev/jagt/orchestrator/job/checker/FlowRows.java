package dev.jagt.orchestrator.job.checker;

import dev.jagt.orchestrator.port.worktree.FlowRunner.FlowResult;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Row;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Status;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A flow naming a criterion decides that criterion's row, a flow naming none is a row of its own.
 * Results apply passed, then unprovable, then failed, so a failed flow outranks a passed one.
 */
final class FlowRows {

  private FlowRows() {}

  /** What a flow result did to one row, from the weakest to the strongest. */
  enum Outcome {
    UNCHANGED,
    EVIDENCE_ADDED,
    MET_BY_FLOW,
    NOT_MET_BY_FLOW,
    OWN_MET,
    OWN_NOT_MET,
    OWN_UNPROVABLE;

    boolean readsDecided() {
      return switch (this) {
        case EVIDENCE_ADDED, MET_BY_FLOW, NOT_MET_BY_FLOW -> true;
        case UNCHANGED, OWN_MET, OWN_NOT_MET, OWN_UNPROVABLE -> false;
      };
    }
  }

  record Decided(Row row, Outcome outcome) {}

  static AcceptanceVerdict merge(
      AcceptanceVerdict verdict, AcceptanceVerdict.Flows ask, List<FlowResult> results) {
    List<Decided> decided = decide(verdict.rows(), ask, results);
    List<Row> rows = decided.stream().map(Decided::row).toList();
    boolean decidedAny = decided.stream().anyMatch(each -> each.outcome().readsDecided());
    boolean unproven = rows.stream().anyMatch(row -> row.status() == Status.UNPROVABLE);
    AcceptanceVerdict merged = verdict.withRows(rows);
    return decidedAny && !unproven ? merged.withQuestion("") : merged;
  }

  /** One entry per criterion: the read's rows in order, then one for each flow naming none. */
  static List<Decided> decide(List<Row> read, AcceptanceVerdict.Flows ask, List<FlowResult> run) {
    List<Row> rows = new ArrayList<>(read);
    List<Outcome> outcomes = new ArrayList<>(Collections.nCopies(rows.size(), Outcome.UNCHANGED));
    List<Decided> separate = new ArrayList<>();
    for (FlowResult result :
        run.stream().sorted(Comparator.comparingInt(FlowRows::rank)).toList()) {
      int index = indexOf(rows, ask.criterionOf(result.flow()));
      if (index < 0) {
        separate.add(own(result));
      } else {
        rows.set(index, decided(rows.get(index), result));
        Outcome outcome = outcomeOn(result);
        outcomes.set(index, Collections.max(List.of(outcomes.get(index), outcome)));
      }
    }
    List<Decided> all = new ArrayList<>();
    for (int index = 0; index < rows.size(); index++) {
      all.add(new Decided(rows.get(index), outcomes.get(index)));
    }
    all.addAll(separate);
    return all;
  }

  private static Outcome outcomeOn(FlowResult result) {
    return switch (result.verdict()) {
      case PASSED -> Outcome.MET_BY_FLOW;
      case FAILED -> Outcome.NOT_MET_BY_FLOW;
      case UNPROVABLE -> Outcome.EVIDENCE_ADDED;
    };
  }

  private static int rank(FlowResult result) {
    return switch (result.verdict()) {
      case PASSED -> 0;
      case UNPROVABLE -> 1;
      case FAILED -> 2;
    };
  }

  private static int indexOf(List<Row> rows, String criterion) {
    if (criterion.isBlank()) {
      return -1;
    }
    for (int index = 0; index < rows.size(); index++) {
      if (names(rows.get(index).criterion(), criterion)) {
        return index;
      }
    }
    return -1;
  }

  private static boolean names(String rowCriterion, String flowCriterion) {
    return !rowCriterion.isBlank()
        && (mentions(rowCriterion, flowCriterion) || mentions(flowCriterion, rowCriterion));
  }

  private static boolean mentions(String text, String token) {
    return Pattern.compile("(?i)(^|\\W)" + Pattern.quote(token.strip()) + "(\\W|$)")
        .matcher(text)
        .find();
  }

  private static Row decided(Row row, FlowResult result) {
    return switch (result.verdict()) {
      case PASSED -> new Row(row.criterion(), Status.MET, said(result));
      case FAILED -> new Row(row.criterion(), Status.NOT_MET, said(result));
      case UNPROVABLE ->
          new Row(row.criterion(), row.status(), (row.evidence() + " | " + said(result)).strip());
    };
  }

  private static Decided own(FlowResult result) {
    Outcome outcome =
        switch (result.verdict()) {
          case PASSED -> Outcome.OWN_MET;
          case FAILED -> Outcome.OWN_NOT_MET;
          case UNPROVABLE -> Outcome.OWN_UNPROVABLE;
        };
    Status status =
        switch (result.verdict()) {
          case PASSED -> Status.MET;
          case FAILED -> Status.NOT_MET;
          case UNPROVABLE -> Status.UNPROVABLE;
        };
    return new Decided(new Row(result.flow(), status, detail(result)), outcome);
  }

  private static String said(FlowResult result) {
    String word = result.verdict().name().toLowerCase(Locale.ROOT);
    String detail = detail(result);
    return "flow " + result.flow() + " " + word + (detail.isEmpty() ? "" : ": " + detail);
  }

  private static String detail(FlowResult result) {
    String paths = result.evidence().stream().map(Path::toString).collect(Collectors.joining(", "));
    return (result.reason() + " " + paths).strip();
  }
}
