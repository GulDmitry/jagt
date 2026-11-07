package dev.jagt.orchestrator.task;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What one QA run found on {@code sha}. {@code failure} non-blank means nothing else was read;
 * {@code waitingOn} names the probe of an environment that did not answer.
 */
public record QaVerdict(
    String sha, String failure, List<Row> rows, String question, String waitingOn) {

  /**
   * The brief steps whose turn QA holds while it reads; the same check run again after a restart is
   * {@code AGAIN}.
   */
  public static final String ACCEPT = "accept";

  public static final String DECLINE = "decline";
  public static final String AGAIN = "again";

  public static boolean isStep(String step) {
    return step.equals(ACCEPT) || step.equals(DECLINE) || step.equals(AGAIN);
  }

  public enum Status {
    MET,
    NOT_MET,
    UNPROVABLE;

    public static Optional<Status> of(String written) {
      String id = written.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
      return java.util.Arrays.stream(values())
          .filter(status -> status.name().equals(id))
          .findFirst();
    }
  }

  public record Row(String criterion, Status status, String evidence) {

    public String line() {
      return "- "
          + criterion
          + " — "
          + status.name().toLowerCase(Locale.ROOT).replace('_', ' ')
          + ": "
          + evidence;
    }
  }

  public QaVerdict {
    rows = List.copyOf(rows);
  }

  public static QaVerdict failed(String sha, String failure) {
    return new QaVerdict(sha, failure, List.of(), "", "");
  }

  /**
   * A verdict with no row proves nothing, and a row not met outranks one unprovable: the fix comes
   * first.
   */
  public QaEnding ending() {
    if (!failure.isBlank()) {
      return QaEnding.FAILED;
    }
    if (!waitingOn.isBlank()) {
      return QaEnding.WAITING;
    }
    if (rows.stream().anyMatch(row -> row.status() == Status.NOT_MET)) {
      return QaEnding.NOT_MET;
    }
    boolean unproven =
        rows.isEmpty()
            || !question.isBlank()
            || rows.stream().anyMatch(row -> row.status() == Status.UNPROVABLE);
    return unproven ? QaEnding.UNPROVABLE : QaEnding.PASSED;
  }

  public List<Row> notMet() {
    return rows.stream().filter(row -> row.status() == Status.NOT_MET).toList();
  }
}
