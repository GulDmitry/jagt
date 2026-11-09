package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.AcceptanceVerdict.Row;
import dev.jagt.orchestrator.task.AcceptanceVerdict.Status;
import java.nio.file.Path;
import java.util.ArrayList;
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

  static AcceptanceVerdict merge(
      AcceptanceVerdict verdict, AcceptanceVerdict.Flows ask, List<FlowResult> results) {
    List<Row> rows = new ArrayList<>(verdict.rows());
    List<Row> separate = new ArrayList<>();
    boolean decidedAny = false;
    for (FlowResult result :
        results.stream().sorted(Comparator.comparingInt(FlowRows::rank)).toList()) {
      int index = indexOf(rows, ask.criterionOf(result.flow()));
      if (index < 0) {
        separate.add(own(result));
      } else {
        rows.set(index, decided(rows.get(index), result));
        decidedAny = true;
      }
    }
    rows.addAll(separate);
    boolean unproven = rows.stream().anyMatch(row -> row.status() == Status.UNPROVABLE);
    AcceptanceVerdict merged = verdict.withRows(rows);
    return decidedAny && !unproven ? merged.withQuestion("") : merged;
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

  private static Row own(FlowResult result) {
    Status status =
        switch (result.verdict()) {
          case PASSED -> Status.MET;
          case FAILED -> Status.NOT_MET;
          case UNPROVABLE -> Status.UNPROVABLE;
        };
    return new Row(result.flow(), status, detail(result));
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
