package dev.jagt.orchestrator.protocol.read;

import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Status;
import java.util.List;

/** What an acceptance answer has to hold before it is acted on. */
public final class RowStatusRead {

  private RowStatusRead() {}

  /** {@code UNREAD} where the word is none of the row statuses. */
  public static Status statusOf(String word) {
    return Status.of(word).orElse(Status.UNREAD);
  }

  /** Empty means the answer can be acted on. */
  public static List<Violation> violations(AcceptanceVerdict verdict) {
    return verdict.rows().stream()
        .filter(row -> row.status() == Status.UNREAD)
        .map(row ->
            new Violation(
                "status of \"" + row.criterion() + "\"", "one of met, not met, unprovable"))
        .toList();
  }

  /** The verdict an answer still unread after its retries is: failed, naming the cause. */
  public static AcceptanceVerdict closed(AcceptanceVerdict verdict) {
    List<Violation> unread = violations(verdict);
    return unread.isEmpty()
        ? verdict
        : AcceptanceVerdict.failed(
            verdict.sha(),
            "the check answered a row status outside the vocabulary: "
                + String.join("; ", unread.stream().map(Violation::toString).toList()));
  }
}
