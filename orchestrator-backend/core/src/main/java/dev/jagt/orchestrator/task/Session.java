package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** What a task's session has signed: times in millis, 0 meaning never; {@code halt} null while nothing has stopped it. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Session(@With long movedAt, @With long workStartedAt, @With long reportedAt, @With @Nullable Halt halt,
                      @With long quietSince, @With @Nullable Window window, @With @Nullable String log,
                      @With boolean stampedAsk) {

    public static Session defaults() {
        return new Session(0, 0, 0, null, 0, null, null, false);
    }

    /** The record of a session just started: no read has seen it, and its start is stamped by the first one. */
    public static Session startedOver() {
        return defaults().withWindow(new Window(Window.State.UNREAD, 0, 0));
    }

    /** A session started over and not yet read takes {@code now} as its start; any other is as it was. */
    public Session readAt(long now) {
        boolean awaiting = window != null && window.state() == Window.State.UNREAD && window.at() == 0;
        return awaiting ? withMovedAt(now).withWindow(null) : this;
    }

    /** The record a file holds, a silence an older file stamped on the task standing as the quiet it was. */
    public static Session migrated(@Nullable Session stored, @Nullable Long legacySilentSince) {
        Session session = stored == null ? defaults() : stored;
        boolean unmarked = session.halt() == null && session.quietSince() == 0;
        return unmarked && legacySilentSince != null && legacySilentSince > 0
                ? session.withQuietSince(legacySilentSince) : session;
    }

    /** Whether its process runs as the last window read showed; null where no read has told. */
    public @Nullable Boolean alive() {
        if (window == null || window.state() == Window.State.UNREAD) {
            return null;
        }
        return window.state() == Window.State.RUNNING;
    }

    /** When it last signed life or stopped, the later of the two; 0 where it has signed nothing. */
    public long idleSince() {
        return Math.max(movedAt, halt == null ? 0 : halt.at());
    }

    /** How a session stopped, when, and why in the words a human reads. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Halt(Kind kind, long at, @Nullable String cause) {

        public enum Kind { IDLE, FAILED, GONE }
    }

    /** What the session's terminal window showed when last read, and when it last printed. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Window(State state, long at, long printedAt) {

        public enum State { MISSING, DEAD_SHELL, RUNNING, UNREAD }
    }
}
