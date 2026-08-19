package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The code host jagt may READ over REST instead of paying a headless model call per review sweep.
 *
 * <p>Blank {@code type} = no host wired, which is the default. Wiring one is opt-in and read-only.
 *
 * @param type    host implementation to activate, e.g. {@code gitlab}; blank = none
 * @param baseUrl the host's root, e.g. {@code https://gitlab.example.com} — a review URL is only read when it
 *                lives under this prefix, so the token can never travel to a host jagt was not pointed at
 * @param token   read-only API token; keep it in the environment, never in a committed file
 */
@ConfigurationProperties(prefix = "orchestrator.code-host")
public record CodeHostProperties(String type, String baseUrl, String token) {

    public CodeHostProperties {
        type = blankToNull(type);
        // A trailing slash would turn the prefix check into a mismatch for the host's own canonical URLs.
        baseUrl = baseUrl == null ? null : blankToNull(baseUrl.strip().replaceAll("/+$", ""));
        token = blankToNull(token);
    }

    public static CodeHostProperties none() {
        return new CodeHostProperties(null, null, null);
    }

    /** True when both halves a REST read needs are present; a half-configured host must not claim URLs. */
    public boolean isUsable() {
        return baseUrl != null && token != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
