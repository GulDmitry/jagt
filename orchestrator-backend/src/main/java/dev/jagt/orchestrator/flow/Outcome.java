package dev.jagt.orchestrator.flow;

/**
 * What a capability reports back. It never names a status: which status this leads to is the table's answer, not
 * the doer's, so the same work can be reached from several statuses without every caller learning the machine.
 *
 * @param message the sentence a human reads
 * @param stamp   the short line the task carries afterwards, or null to leave what it says alone
 * @param cause   what went wrong, for an outcome that refuses — kept so a log still has the original
 */
public record Outcome(Kind kind, String message, String stamp, Throwable cause) {

    public enum Kind {
        /** The work was done here and now. */
        OK(false),
        /** Nothing happened, and nothing was wrong — the same work was already under way. */
        NOTHING(false),
        /** Handed to the agent to finish — jagt is not the one that will report it done. */
        RELAYED(false),
        /** Stopped on something a human resolves, in a place the message names. */
        CONFLICT(false),
        /** Part of it landed. Always a refusal: a half-written shared branch must not read as success. */
        PARTIAL(true),
        /** The task itself is gone; there is nothing left to stamp. */
        GONE(false);

        private final boolean refuses;

        Kind(boolean refuses) {
            this.refuses = refuses;
        }

        /** Whether the caller must be told this as a failure rather than as an answer. */
        public boolean refuses() {
            return refuses;
        }
    }

    public static Outcome ok(String message, String stamp) {
        return new Outcome(Kind.OK, message, stamp, null);
    }

    /** For work that changes nothing about the task itself — looking at it, restarting its session. */
    public static Outcome ok(String message) {
        return new Outcome(Kind.OK, message, null, null);
    }

    public static Outcome nothing(String message) {
        return new Outcome(Kind.NOTHING, message, null, null);
    }

    public static Outcome relayed(String message, String stamp) {
        return new Outcome(Kind.RELAYED, message, stamp, null);
    }

    public static Outcome conflict(String message, String stamp) {
        return new Outcome(Kind.CONFLICT, message, stamp, null);
    }

    public static Outcome partial(String message, String stamp, Throwable cause) {
        return new Outcome(Kind.PARTIAL, message, stamp, cause);
    }

    public static Outcome gone(String message) {
        return new Outcome(Kind.GONE, message, null, null);
    }
}
