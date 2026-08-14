package dev.jagt.orchestrator.model;

/**
 * The reserved openings a status message may carry. {@link TaskStatus} says WHERE a task is; this says what the
 * agent is reporting about it — and for a review round that is what decides whose move it is, since all three
 * outcomes (fixed code, a question, nothing to change) end the round at the same status.
 *
 * <p>One vocabulary in one place: the message is parsed here and nowhere else, so the dashboard line and the
 * projection cannot disagree about what {@code awaiting: …} means.
 */
public enum AgentReport {

    /** The agent stopped rather than guess; the question rides in the message. */
    QUESTION,
    /** A round that changed no code: every comment was already handled or was answered with a push-back. */
    NO_CHANGES,
    PLAIN;

    public static AgentReport of(String message) {
        String opening = message == null ? "" : message.strip().toLowerCase();
        if (opening.startsWith("awaiting")) {
            return QUESTION;
        }
        if (opening.startsWith("no changes") || opening.startsWith("no-changes")) {
            return NO_CHANGES;
        }
        return PLAIN;
    }

    /** The message without its marker, for a line that supplies its own label. */
    public String detailOf(String message) {
        return message == null ? "" : message.replaceFirst("(?i)^(awaiting|no[ -]changes):?\\s*", "");
    }
}
