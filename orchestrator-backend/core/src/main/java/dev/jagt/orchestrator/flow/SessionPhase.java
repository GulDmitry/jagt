package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import org.jspecify.annotations.Nullable;

/** The session region's states, each decided from the record alone. */
public enum SessionPhase {
    NONE, WORKING, IDLE, QUIET, FAILED, GONE;

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

    /** Since when an agent that should be working has stopped, or 0 where it has not, or where its status idles by design. */
    public static long silentSince(TaskState task) {
        if (!FlowRules.watched(task.status()) || task.report() == AgentReport.WAIT) {
            return 0;
        }
        Session session = task.session();
        Session.Halt halt = session.halt();
        return switch (of(session)) {
            case FAILED, GONE -> halt == null ? 0 : halt.at();
            case QUIET -> session.quietSince();
            default -> 0;
        };
    }

    public static boolean silent(TaskState task) {
        return silentSince(task) > 0;
    }

    /** The words a human is owed for the silence, or null where the session said nothing and the surface speaks for itself. */
    public static @Nullable String silentBecause(TaskState task) {
        Session session = task.session();
        Session.Halt halt = session.halt();
        if (!silent(task)) {
            return null;
        }
        boolean running = session.alive() == Boolean.TRUE;
        if (halt == null) {
            return running ? "no sign of life, process running" : null;
        }
        String cause = halt.cause();
        return switch (halt.kind()) {
            case GONE -> cause == null ? "the session ended" : "the session ended: " + cause;
            case FAILED -> cause == null ? "its turn failed" : "its turn failed: " + cause;
            case IDLE -> "its turn ended and nothing has moved since" + (running ? ", process running" : "");
        };
    }
}
