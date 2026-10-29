package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * What a capability reports back. It never DECIDES a status — which one this leads to is the table's answer —
 * though its sentence may tell a human which one was reached.
 * {@code stamp} is the short line the task carries afterwards, or null to leave what it says alone; {@code stampMeans}
 * is what that line reports.
 */
public record Outcome(Kind kind, String message, @Nullable String stamp, AgentReport stampMeans,
                      @Nullable Throwable cause) {

    @RequiredArgsConstructor
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

        /** Whether the caller must be told this as a failure rather than as an answer. */
        public boolean refuses() {
            return refuses;
        }
    }

    public static Outcome ok(String message, String stamp) {
        return new Outcome(Kind.OK, message, stamp, AgentReport.PLAIN, null);
    }

    /** For work that changes nothing about the task itself — looking at it, restarting its session. */
    public static Outcome ok(String message) {
        return new Outcome(Kind.OK, message, null, AgentReport.PLAIN, null);
    }

    public static Outcome nothing(String message) {
        return new Outcome(Kind.NOTHING, message, null, AgentReport.PLAIN, null);
    }

    public static Outcome relayed(String message, String stamp) {
        return new Outcome(Kind.RELAYED, message, stamp, AgentReport.PLAIN, null);
    }

    public static Outcome conflict(String message, String stamp) {
        return new Outcome(Kind.CONFLICT, message, stamp, AgentReport.PLAIN, null);
    }

    public static Outcome partial(String message, @Nullable String stamp, @Nullable Throwable cause) {
        return new Outcome(Kind.PARTIAL, message, stamp, AgentReport.PLAIN, cause);
    }

    public static Outcome gone(String message) {
        return new Outcome(Kind.GONE, message, null, AgentReport.PLAIN, null);
    }

    /** The same, its stamp a question for the human. */
    public Outcome asking() {
        return new Outcome(kind, message, stamp, AgentReport.QUESTION, cause);
    }
}
