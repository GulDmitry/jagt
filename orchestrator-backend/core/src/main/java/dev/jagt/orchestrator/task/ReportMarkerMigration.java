package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads a task stored before it carried its {@link AgentReport}, when the meaning was a marker opening its line.
 * The one place a line is parsed for a meaning, and only on load.
 */
final class ReportMarkerMigration {

    private static final Pattern MARKER = Pattern.compile(
            "(?i)^\\s*(?:outcome\\s*[:=]\\s*(?:question|no[ _-]changes|progress)"
                    + "|awaiting(?:\\s+permission\\b)?|no[ _-]changes)\\b[\\s:.]*(?:[–—-]\\s+)?");

    private ReportMarkerMigration() {
    }

    static AgentReport reportOf(@Nullable String message) {
        var matcher = MARKER.matcher(message == null ? "" : message);
        if (!matcher.find()) {
            return AgentReport.PLAIN;
        }
        String marker = matcher.group().toLowerCase(Locale.ROOT).replaceAll("[ _-]", "");
        if (marker.contains("permission")) {
            return AgentReport.PERMISSION;
        }
        if (marker.contains("question") || marker.contains("awaiting")) {
            return AgentReport.QUESTION;
        }
        return marker.contains("nochanges") ? AgentReport.NO_CHANGES : AgentReport.PLAIN;
    }

    static @Nullable String lineOf(@Nullable String message) {
        if (message == null) {
            return null;
        }
        String line = MARKER.matcher(message).replaceFirst("");
        return line.isBlank() ? null : line;
    }
}
