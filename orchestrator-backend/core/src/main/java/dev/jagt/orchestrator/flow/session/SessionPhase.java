package dev.jagt.orchestrator.flow.session;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/** The session region's states, each decided from the record alone. */
public enum SessionPhase {
  NONE,
  WORKING,
  IDLE,
  QUIET,
  FAILED,
  GONE;

  /** Whether an agent session is up: working, idle or quiet, not never started, failed or gone. */
  public boolean live() {
    return this == WORKING || this == IDLE || this == QUIET;
  }

  public static SessionPhase of(Session session) {
    Session.Halt halt = session.halt();
    if (halt != null && halt.kind() == Session.Halt.Kind.FAILED) {
      return FAILED;
    }
    if (halt != null && halt.kind() == Session.Halt.Kind.GONE) {
      return GONE;
    }
    if (session.quietSince() > 0) {
      return QUIET;
    }
    if (halt != null) {
      return IDLE;
    }
    return session.movedAt() > 0 ? WORKING : NONE;
  }

  /**
   * Whether the session's last sign, in any phase, is past the cache lifetime; false where it
   * signed nothing.
   */
  public static boolean cold(Session session, AgentRuntime runtime, Duration cacheLifetime) {
    long lastSign = session.idleSince();
    return lastSign > 0 && runtime.pastCacheLifetime(lastSign, cacheLifetime);
  }

  /**
   * Since when an agent that should be working has stopped, or 0 where it has not, or where its
   * status idles by design.
   */
  public static long silentSince(TaskState task) {
    if (!FlowRules.watched(task.standing().status())
        || task.standing().report() == AgentReport.WAIT) {
      return 0;
    }
    Session session = task.work().session();
    Session.Halt halt = session.halt();
    return switch (of(session)) {
      case FAILED, GONE -> halt == null ? 0 : halt.at();
      case QUIET -> session.quietSince();
      default -> 0;
    };
  }

  /** {@link #silentSince}, but 0 where the wait follows a question the agent itself asked. */
  public static long silentSinceUnasked(TaskState task) {
    long since = silentSince(task);
    Session.Halt halt = task.work().session().halt();
    boolean asking =
        task.standing().report().asks() && (halt == null || halt.kind() == Session.Halt.Kind.IDLE);
    return asking ? 0 : since;
  }

  public static boolean silent(TaskState task) {
    return silentSince(task) > 0;
  }

  /**
   * The words a human is owed for the silence, or null where the session said nothing and the
   * surface speaks for itself.
   */
  public static @Nullable String silentBecause(TaskState task) {
    Session session = task.work().session();
    Session.Halt halt = session.halt();
    if (!silent(task)) {
      return null;
    }
    boolean running = Boolean.TRUE.equals(session.alive());
    if (halt == null) {
      return running ? "no sign of life, process running" : null;
    }
    String cause = halt.cause();
    return switch (halt.kind()) {
      case GONE -> cause == null ? "the session ended" : "the session ended: " + cause;
      case FAILED -> cause == null ? "its turn failed" : "its turn failed: " + cause;
      case IDLE ->
          "its turn ended and nothing has moved since" + (running ? ", process running" : "");
    };
  }
}
