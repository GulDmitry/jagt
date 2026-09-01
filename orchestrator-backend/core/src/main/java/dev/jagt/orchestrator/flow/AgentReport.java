package dev.jagt.orchestrator.flow;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The reserved openings a status message may carry: what the agent is reporting, which for a review round decides
 * whose move it is, since all three outcomes end the round at the same status. The message is parsed here and
 * nowhere else. Which words may open a message on their own follows how ordinary they are as prose — {@code
 * awaiting} and {@code no_changes} are nobody's sentence, {@code question} and {@code progress} are, so those two
 * count only where the word {@code outcome} names them.
 */
public enum AgentReport {

    /** The agent stopped rather than guess; the question rides in the message. */
    QUESTION,
    /** A round that changed no code: every comment was already handled or was answered with a push-back. */
    NO_CHANGES,
    PLAIN;

    private static final Pattern MARKER = Pattern.compile(
            "(?i)^\\s*(?:outcome\\s*[:=]\\s*(?:question|no[ _-]changes|progress)"
                    + "|awaiting|no[ _-]changes)\\b[\\s:.]*(?:[–—-]\\s+)?");

    public static AgentReport of(String message) {
        var matcher = MARKER.matcher(message == null ? "" : message);
        if (!matcher.find()) {
            return PLAIN;
        }
        String marker = matcher.group().toLowerCase(Locale.ROOT).replaceAll("[ _-]", "");
        if (marker.contains("question") || marker.contains("awaiting")) {
            return QUESTION;
        }
        return marker.contains("nochanges") ? NO_CHANGES : PLAIN;
    }

    /** The message without its marker, for a line that supplies its own label. */
    public String detailOf(String message) {
        return withoutMarker(message);
    }

    /** The same, for a caller that is DECIDING which marker belongs there rather than reading one. */
    public static String withoutMarker(String message) {
        return message == null ? "" : MARKER.matcher(message).replaceFirst("");
    }
}
