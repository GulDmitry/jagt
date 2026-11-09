package dev.jagt.orchestrator.task;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.With;

/**
 * What one acceptance run found on {@code sha}. {@code failure} non-blank means nothing else was
 * read; {@code waitingOn} names the probe of an environment that did not answer.
 */
public record AcceptanceVerdict(
    String sha, String failure, List<Row> rows, String question, String waitingOn, Flows flows) {

  /** The flows a read named, each with the criterion it checks, and the base url they run on. */
  @With
  public record Flows(List<Flow> named, String environment) {

    /** A blank {@code criterion} means the flow decides no row of the read's. */
    public record Flow(String id, String criterion) {}

    public Flows {
      named = List.copyOf(named);
    }

    public static Flows none() {
      return new Flows(List.of(), "");
    }

    public List<String> ids() {
      return named.stream().map(Flow::id).toList();
    }

    public String criterionOf(String id) {
      return named.stream()
          .filter(flow -> flow.id().equals(id))
          .map(Flow::criterion)
          .findFirst()
          .orElse("");
    }
  }

  /**
   * The brief steps whose turn acceptance holds while it reads; the same check run again after a
   * restart is {@code AGAIN}.
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

    public String tableLine() {
      return "| " + criterion + " | " + status + " | " + evidence + " |";
    }
  }

  public AcceptanceVerdict {
    rows = List.copyOf(rows);
  }

  public AcceptanceVerdict withRows(List<Row> decided) {
    return new AcceptanceVerdict(sha, failure, decided, question, waitingOn, flows);
  }

  public AcceptanceVerdict withQuestion(String asked) {
    return new AcceptanceVerdict(sha, failure, rows, asked, waitingOn, flows);
  }

  public AcceptanceVerdict withFlows(Flows named) {
    return new AcceptanceVerdict(sha, failure, rows, question, waitingOn, named);
  }

  public String report(String role) {
    return "# "
        + role.toUpperCase(Locale.ROOT)
        + " report, "
        + sha
        + "\n\n| criterion | status | evidence |\n|---|---|---|\n"
        + rows.stream().map(Row::tableLine).collect(java.util.stream.Collectors.joining("\n"))
        + "\n";
  }

  public static AcceptanceVerdict failed(String sha, String failure) {
    return new AcceptanceVerdict(sha, failure, List.of(), "", "", Flows.none());
  }

  /**
   * A verdict with no row proves nothing, and a row not met outranks one unprovable: the fix comes
   * first.
   */
  public AcceptanceEnding ending() {
    if (!failure.isBlank()) {
      return AcceptanceEnding.FAILED;
    }
    if (!waitingOn.isBlank()) {
      return AcceptanceEnding.WAITING;
    }
    if (rows.stream().anyMatch(row -> row.status() == Status.NOT_MET)) {
      return AcceptanceEnding.NOT_MET;
    }
    boolean unproven =
        rows.isEmpty()
            || !question.isBlank()
            || rows.stream().anyMatch(row -> row.status() == Status.UNPROVABLE);
    return unproven ? AcceptanceEnding.UNPROVABLE : AcceptanceEnding.PASSED;
  }

  public List<Row> notMet() {
    return rows.stream().filter(row -> row.status() == Status.NOT_MET).toList();
  }
}
