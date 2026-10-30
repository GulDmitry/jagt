package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Session;
import org.jspecify.annotations.Nullable;

/**
 * What a session did, as data, stamped with the moment it arrived; {@link TaskReducer} is the one place that records
 * it. A halt keeps a tie with a call, and loses one to the start of work.
 */
public sealed interface SessionSign {

    Session applyTo(Session session, long now);

    record WorkStarted() implements SessionSign {
        @Override
        public Session applyTo(Session session, long now) {
            return older(session, now, false) ? session : alive(session, now).withWorkStartedAt(now);
        }
    }

    record Called() implements SessionSign {
        @Override
        public Session applyTo(Session session, long now) {
            return older(session, now, true) ? session : alive(session, now);
        }
    }

    record Reported() implements SessionSign {
        @Override
        public Session applyTo(Session session, long now) {
            return session.withReportedAt(now);
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

    record Ended(@Nullable String reason) implements SessionSign {
        @Override
        public Session applyTo(Session session, long now) {
            return halted(session, new Session.Halt(Session.Halt.Kind.GONE, now, reason), now);
        }
    }

    /** The session lives on in a fresh start of the same CLI: its earlier failure or end no longer holds. */
    record Restarted(@Nullable String reason) implements SessionSign {
        @Override
        public Session applyTo(Session session, long now) {
            return halted(session, new Session.Halt(Session.Halt.Kind.IDLE, now, reason), now);
        }
    }

    /** Nothing has moved for longer than the caller allows; the silence counts from the last sign. */
    record WentQuiet() implements SessionSign {
        @Override
        public Session applyTo(Session session, long now) {
            Session.Halt halt = session.halt();
            return session.withQuietSince(Math.max(1, halt == null ? session.movedAt() : halt.at()));
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
