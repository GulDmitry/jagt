package dev.jagt.orchestrator.protocol.read;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.protocol.Violation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** One role's reading of a review round. */
public final class RoundRead {

  public static final List<String> VERDICTS =
      Arrays.stream(Verdict.values()).filter(v -> v != Verdict.UNREAD).map(Verdict::word).toList();
  public static final List<String> SEVERITIES =
      Arrays.stream(Finding.Severity.values()).map(Finding.Severity::id).toList();
  public static final List<String> KINDS =
      List.of("behaviour", "missing", "scope", "test", "structure");

  public static final Schema SCHEMA =
      Schema.answer()
          .required("failure", "string", null)
          .choiceRequired("verdict", VERDICTS, null)
          .records(
              "findings",
              null,
              List.of("file", "anchor", "kind", "issue", "pattern", "severity"),
              Map.of("severity", SEVERITIES, "kind", KINDS))
          .text("question", null)
          .records("premises", null, List.of("claim", "provenBy"))
          .text("newTask", null)
          .text("press", null);

  private RoundRead() {}

  /** {@code UNREAD} where the word is none of {@link #VERDICTS}. */
  public static Verdict verdictOf(String word) {
    return Verdict.ofWord(word);
  }

  /** A blank word is words; any other names the move. */
  public static Press pressOf(String word) {
    return Press.ofId(word);
  }

  /** Empty means the answer can be acted on. */
  public static List<Violation> violations(Judgement judged) {
    return judged.verdict() == Verdict.UNREAD && judged.failure().isBlank()
        ? List.of(new Violation("verdict", "one of " + String.join(", ", VERDICTS)))
        : List.of();
  }

  /** The judgement an answer still unread after its retries is: failed, naming the cause. */
  public static Judgement closed(Judgement judged) {
    List<Violation> unread = violations(judged);
    return unread.isEmpty()
        ? judged
        : Judgement.failed(
            "the review answered outside the vocabulary: "
                + String.join("; ", unread.stream().map(Violation::toString).toList()));
  }
}
