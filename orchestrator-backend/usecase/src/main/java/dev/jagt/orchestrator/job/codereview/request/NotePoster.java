package dev.jagt.orchestrator.job.codereview.request;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.NoteAnchor;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import dev.jagt.orchestrator.task.review.AiLine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The one caller of {@link RequestNotes#post}: a note is posted once, opens with the line saying an
 * AI wrote it, and carries a hidden key that is read back from the host before every post, so a
 * restart between a post and the ledger write posts nothing twice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotePoster {

  private static final int DIGEST_LENGTH = 16;
  public static final String MARKER = "jagt-review:";
  private static final String THREAD_MARKER = "jagt-thread:";

  /** How a post ended; only {@code POSTED} wrote to the host. */
  public enum Outcome {
    POSTED,
    DUPLICATE,
    SKIPPED,
    DROPPED,
    FAILED
  }

  private final RequestNotes notes;
  private final ReviewLedger ledger;

  /**
   * A note on the request for one finding, on the line {@code anchor} names where it names one, or
   * an answer to {@code replyTo}. Dropped where the request is over, its head is no longer {@code
   * head}, or it can no longer be read. A finding with no diff line, a line the host refuses, and a
   * finding an open thread of the role's own already says ({@code threadKey}) post nothing.
   */
  public Outcome post(
      Subject subject,
      String head,
      String key,
      String threadKey,
      String text,
      String replyTo,
      NoteAnchor anchor) {
    Answer<RequestChange> change = notes.change(subject.project(), subject.url());
    if (change.facts().isEmpty()
        || !change.facts().get().headSha().equals(head)
        || change.facts().get().state() != RequestState.OPEN) {
      return Outcome.DROPPED;
    }
    Answer<List<RequestNote>> read = notes.notes(subject.project(), subject.url());
    if (read.facts().isEmpty()) {
      return Outcome.FAILED;
    }
    if (read.facts().get().stream().anyMatch(note -> note.body().contains(MARKER + key))) {
      remember(subject, key);
      return Outcome.DUPLICATE;
    }
    boolean onRequest = replyTo.isBlank();
    if (onRequest && !anchor.inline()) {
      warn(subject, "the finding has no line of the diff", "no note is posted");
      return Outcome.SKIPPED;
    }
    if (onRequest && openThreadSays(read.facts().get(), threadKey)) {
      remember(subject, key);
      return Outcome.DUPLICATE;
    }
    Answer<String> posted =
        notes.post(
            subject.project(),
            subject.url(),
            new NoteDraft(body(text, key, threadKey), replyTo, anchor));
    if (posted.facts().isEmpty() && onRequest) {
      warn(subject, posted.failure(), "no note is posted");
      return Outcome.SKIPPED;
    }
    if (posted.facts().isEmpty()) {
      warn(subject, posted.failure(), "the note is tried again on the next read");
      return Outcome.FAILED;
    }
    remember(subject, key);
    return Outcome.POSTED;
  }

  private static boolean openThreadSays(List<RequestNote> read, String threadKey) {
    return !threadKey.isBlank()
        && read.stream()
            .anyMatch(note ->
                note.kind() == AuthorKind.ACTOR
                    && note.resolution() != Resolution.RESOLVED
                    && note.body().contains(THREAD_MARKER + threadKey));
  }

  private void warn(Subject subject, String cause, String effect) {
    log.atWarn()
        .setMessage("code review note not posted")
        .addKeyValue("request", subject.url())
        .addKeyValue("cause", cause)
        .addKeyValue("effect", effect)
        .log();
  }

  /** The short digest that makes a note, an answer or a brief the same one in a later run. */
  public static String digest(String... parts) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(String.join("\u0000", parts).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash).substring(0, DIGEST_LENGTH);
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException("SHA-256 is part of every JVM", missing);
    }
  }

  static String body(String text, String key, String threadKey) {
    String thread = threadKey.isBlank() ? "" : "\n<!-- " + THREAD_MARKER + threadKey + " -->";
    return AiLine.TEXT + "\n\n" + text.strip() + "\n\n<!-- " + MARKER + key + " -->" + thread;
  }

  private void remember(Subject subject, String key) {
    ledger.update(
        subject.url(),
        subject.project(),
        subject.taskId(),
        row -> {
          List<String> keys = new ArrayList<>(row.keys());
          keys.add(key);
          return row.withKeys(keys);
        });
  }
}
