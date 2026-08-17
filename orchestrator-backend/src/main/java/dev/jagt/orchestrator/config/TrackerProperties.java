package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The issue tracker jagt may READ over its own API instead of paying a headless model call per ticket.
 *
 * <p>Blank {@code type} = none wired, which is the default: a {@code do} then reads the ticket through the
 * assistant exactly as before. Wiring one is opt-in and read-only — jagt never transitions, comments on or
 * assigns an issue.
 *
 * @param type    tracker implementation to activate, e.g. {@code jira}; blank = none
 * @param baseUrl the tracker's root, e.g. {@code https://tracker.example.com} — a ticket URL is only read when
 *                it lives under this prefix, so the token cannot travel to a tracker jagt was not pointed at
 * @param user    the account the token belongs to, where the tracker authenticates a user rather than a token
 * @param token   read-only API token; keep it in the environment, never in a committed file
 */
@ConfigurationProperties(prefix = "orchestrator.tracker")
public record TrackerProperties(String type, String baseUrl, String user, String token) {

    public TrackerProperties {
        type = blankToNull(type);
        // A trailing slash would turn the prefix check into a mismatch for the tracker's own canonical URLs.
        baseUrl = baseUrl == null ? null : blankToNull(baseUrl.strip().replaceAll("/+$", ""));
        user = blankToNull(user);
        token = blankToNull(token);
    }

    public static TrackerProperties none() {
        return new TrackerProperties(null, null, null, null);
    }

    /** True when both halves a read needs are present; a half-configured tracker must not claim tickets. */
    public boolean isUsable() {
        return baseUrl != null && token != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
