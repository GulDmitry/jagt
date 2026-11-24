package dev.jagt.orchestrator.job.codereview.request;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteAnchor;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
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

  /** How a post ended; only {@code POSTED} wrote to the host. */
  public enum Outcome {
    POSTED,
    DUPLICATE,
    DROPPED,
    FAILED
  }

  private final RequestNotes notes;
  private final ReviewLedger ledger;

  /**
   * A note on the request for one finding, on the line {@code anchor} names where it names one, or
   * an answer to {@code replyTo}. Dropped where the request is over, its head is no longer {@code
   * head}, or it can no longer be read. A line the host refuses is posted on the request instead.
   */
  public Outcome post(
      Subject subject, String head, String key, String text, String replyTo, NoteAnchor anchor) {
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
    String body = body(text, key);
    Answer<String> posted =
        notes.post(subject.project(), subject.url(), new NoteDraft(body, replyTo, anchor));
    if (posted.facts().isEmpty() && anchor.inline()) {
      log.atInfo()
          .setMessage("code review inline note refused")
          .addKeyValue("request", subject.url())
          .addKeyValue("cause", posted.failure())
          .addKeyValue("effect", "the note is posted on the request instead")
          .log();
      posted = notes.post(subject.project(), subject.url(), NoteDraft.replyTo(body, replyTo));
    }
    if (posted.facts().isEmpty()) {
      log.atWarn()
          .setMessage("code review note not posted")
          .addKeyValue("request", subject.url())
          .addKeyValue("cause", posted.failure())
          .addKeyValue("effect", "the note is tried again on the next read")
          .log();
      return Outcome.FAILED;
    }
    remember(subject, key);
    return Outcome.POSTED;
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

  static String body(String text, String key) {
    return AiLine.TEXT + "\n\n" + text.strip() + "\n\n<!-- " + MARKER + key + " -->";
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
