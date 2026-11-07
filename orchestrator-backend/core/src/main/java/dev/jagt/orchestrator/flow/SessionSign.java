package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Session;
import org.jspecify.annotations.Nullable;

/**
 * What a session did, as data, stamped with the moment it arrived; {@link TaskReducer} is the one
 * place that records it. A halt keeps a tie with a call, and loses one to the start of work.
 */
public sealed interface SessionSign {

  Session applyTo(Session session, long now);

  record WorkStarted() implements SessionSign {
    @Override
    public Session applyTo(Session session, long now) {
      return older(session, now, false)
          ? session
          : alive(session, now).withWorkStartedAt(now).withStampedAsk(false);
    }
  }

  record Called() implements SessionSign {
    @Override
    public Session applyTo(Session session, long now) {
      return older(session, now, true) ? session : alive(session, now);
    }
  }

  record ToolUsed() implements SessionSign {
    @Override
    public Session applyTo(Session session, long now) {
      return older(session, now, true) ? session : alive(session, now);
    }
  }

  record LogNamed(String log) implements SessionSign {
    @Override
    public Session applyTo(Session session, long now) {
      return session.withLog(log);
    }
  }

  record Reported() implements SessionSign {
    @Override
    public Session applyTo(Session session, long now) {
      return session.withReportedAt(now).withStampedAsk(false);
    }
  }

  /**
   * The session cannot go on until a human answers, as its harness or its window shows rather than
   * as it said.
   */
  record Asked(@Nullable String cause, Channel channel) implements SessionSign {

    public enum Channel {
      HOOK,
      SCREEN
    }

    @Override
    public Session applyTo(Session session, long now) {
      return session.withStampedAsk(true);
    }
  }

  record TurnEnded(@Nullable String log) implements SessionSign {
    @Override
    public Session applyTo(Session session, long now) {
      return halted(session, new Session.Halt(Session.Halt.Kind.IDLE, now, null), now)
          .withLog(log == null ? session.log() : log);
    }
  }

  record TurnFailed(@Nullable String cause) implements SessionSign {
    @Override
    public Session applyTo(Session session, long now) {
      return halted(session, new Session.Halt(Session.Halt.Kind.FAILED, now, cause), now);
    }
  }

  /**
   * An end sign carries no start to compare, so one before the first read can only be the replaced
   * CLI's.
   */
  record Ended(@Nullable String reason) implements SessionSign {
    @Override
    public Session applyTo(Session session, long now) {
      if (session.awaitingFirstRead()) {
        return session;
      }
      return halted(session, new Session.Halt(Session.Halt.Kind.GONE, now, reason), now);
    }
  }

  /**
   * The session lives on in a fresh start of the same CLI: its earlier failure or end no longer
   * holds.
   */
  record Restarted(@Nullable String reason) implements SessionSign {
    @Override
    public Session applyTo(Session session, long now) {
      return halted(session, new Session.Halt(Session.Halt.Kind.IDLE, now, reason), now);
    }
  }

  /**
   * What a look at the session found, with the signs that need no hook: the window's last print,
   * the file the session appends to, and the task's own last activity. The window is {@code UNREAD}
   * where it was not asked. A halt older than a later sign is dropped; output alone never undoes a
   * halt, a repaint prints too; a running agent process ends an end. A window holding no agent
   * process ends the session, its reason what the agent printed on the way out.
   */
  record Read(
      Session.Window.State window,
      long printedAt,
      long logAt,
      long activeAt,
      long staleAfter,
      @Nullable String exitTail)
      implements SessionSign {

    private Session.@Nullable Halt standing(Session.@Nullable Halt halt, long later, long now) {
      if (halt == null || halt.at() < later) {
        return null;
      }
      boolean ranAgain =
          halt.kind() == Session.Halt.Kind.GONE
              && window == Session.Window.State.RUNNING
              && now > halt.at();
      return ranAgain ? null : halt;
    }

    private boolean startGap(Session held) {
      return window == Session.Window.State.MISSING && held.awaitingFirstRead();
    }

    public boolean processGone() {
      return window == Session.Window.State.DEAD_SHELL || window == Session.Window.State.MISSING;
    }

    @Override
    public Session applyTo(Session held, long now) {
      Session session = held.readAt(now);
      if (startGap(held)) {
        return session;
      }
      long later = Math.max(session.movedAt(), Math.max(logAt, activeAt));
      Session.Halt halt = standing(session.halt(), later, now);
      if (processGone()) {
        return session
            .withHalt(goneHalt(halt, now))
            .withQuietSince(0)
            .withWindow(windowAfter(session, now));
      }
      long last = Math.max(later, halt == null ? 0 : halt.at());
      if (halt == null && printedAt > last) {
        last = printedAt;
      }
      boolean stale = halt == null || halt.kind() == Session.Halt.Kind.IDLE;
      long quiet = stale && last > 0 && now - last >= staleAfter ? last : 0;
      return session.withHalt(halt).withQuietSince(quiet).withWindow(windowAfter(session, now));
    }

    private Session.Halt goneHalt(Session.@Nullable Halt standing, long now) {
      return standing != null && standing.kind() == Session.Halt.Kind.GONE
          ? standing
          : new Session.Halt(Session.Halt.Kind.GONE, now, exitTail);
    }

    private Session.@Nullable Window windowAfter(Session session, long now) {
      Session.Window held = session.window();
      if (window == Session.Window.State.UNREAD) {
        return held;
      }
      return held != null && held.state() == window && held.printedAt() == printedAt
          ? held
          : new Session.Window(window, now, printedAt);
    }
  }

  private static boolean older(Session session, long now, boolean tieKeepsHalt) {
    Session.Halt halt = session.halt();
    return halt != null && (tieKeepsHalt ? now <= halt.at() : now < halt.at());
  }

  private static Session alive(Session session, long now) {
    return session.withHalt(null).withQuietSince(0).withMovedAt(Math.max(session.movedAt(), now));
  }

  private static Session halted(Session session, Session.Halt halt, long now) {
    return session.withHalt(halt).withQuietSince(0).withMovedAt(Math.max(session.movedAt(), now));
  }
}
