package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Session;

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
}
