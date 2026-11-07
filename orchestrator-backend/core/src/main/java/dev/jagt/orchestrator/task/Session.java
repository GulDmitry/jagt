package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * What a task's session has signed: times in millis, 0 meaning never; {@code halt} null while
 * nothing has stopped it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Session(
    @With long movedAt,
    @With long workStartedAt,
    @With long reportedAt,
    @With @Nullable Halt halt,
    @With long quietSince,
    @With @Nullable String log,
    @With boolean stampedAsk,
    @With @JsonUnwrapped Start start) {

  public static Session defaults() {
    return new Session(0, 0, 0, null, 0, null, false, new Start(0, null));
  }

  public int generation() {
    return start.generation();
  }

  public @Nullable Window window() {
    return start.window();
  }

  public Session withGeneration(int generation) {
    return withStart(new Start(generation, start.window()));
  }

  public Session withWindow(@Nullable Window window) {
    return withStart(new Start(start.generation(), window));
  }

  /**
   * The record of a session just started: no read has seen it, and its start is stamped by the
   * first one; {@code generation} counts the starts.
   */
  public static Session startedOver(Session previous) {
    return defaults()
        .withGeneration(previous.generation() + 1)
        .withWindow(new Window(Window.State.UNREAD, 0, 0));
  }

  /**
   * A session started over and not yet read takes {@code now} as its start; any other is as it was.
   */
  public Session readAt(long now) {
    return awaitingFirstRead() ? withMovedAt(now).withWindow(null) : this;
  }

  /** Started over and not yet read. */
  public boolean awaitingFirstRead() {
    Window window = window();
    return window != null && window.state() == Window.State.UNREAD && window.at() == 0;
  }

  /**
   * The record a file holds, a silence an older file stamped on the task standing as the quiet it
   * was.
   */
  public static Session migrated(@Nullable Session stored, @Nullable Long legacySilentSince) {
    Session session = stored == null ? defaults() : stored;
    boolean unmarked = session.halt() == null && session.quietSince() == 0;
    return unmarked && legacySilentSince != null && legacySilentSince > 0
        ? session.withQuietSince(legacySilentSince)
        : session;
  }

  /** Whether its process runs as the last window read showed; null where no read has told. */
  public @Nullable Boolean alive() {
    Window window = window();
    if (window == null || window.state() == Window.State.UNREAD) {
      return null;
    }
    return window.state() == Window.State.RUNNING;
  }

  /**
   * When it last signed life, stopped or was last seen by a read, the latest of the three; 0 where
   * it has signed nothing.
   */
  public long idleSince() {
    return Math.max(Math.max(movedAt, quietSince), halt == null ? 0 : halt.at());
  }

  /** Which start of the session this is, and what the last read of its window made of it. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Start(int generation, @Nullable Window window) {}

  /** How a session stopped, when, and why in the words a human reads. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Halt(Kind kind, long at, @Nullable String cause) {

    public enum Kind {
      IDLE,
      FAILED,
      GONE
    }
  }

  /** What the session's terminal window showed when last read, and when it last printed. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Window(State state, long at, long printedAt) {

    public enum State {
      MISSING,
      DEAD_SHELL,
      RUNNING,
      UNREAD
    }
  }
}
