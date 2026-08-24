package dev.jagt.orchestrator.adapter.tracker;

import dev.jagt.orchestrator.port.Tracker;
import dev.jagt.orchestrator.config.TrackerProperties;
import dev.jagt.orchestrator.port.JsonHttp;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Only the {@code v2} API is called: Cloud and Data Center both serve it, and the three fields read here are
 * identical in v2 and v3, so asking for the newer one would drop every self-hosted install for nothing.
 *
 * <p>Authentication follows what the token IS: with a {@code user} configured the pair is sent as basic
 * credentials (Cloud issues its API tokens against an account), without one the token goes as a bearer (a
 * personal access token stands on its own).
 */
@Component
@ConditionalOnProperty(name = "orchestrator.tracker.type", havingValue = "jira")
@Slf4j
public class JiraTracker implements Tracker {

    private static final String KEY = "[A-Za-z][A-Za-z0-9_]*-[0-9]+";
    private static final Pattern BARE_KEY = Pattern.compile("^" + KEY + "$");
    /** The link Jira itself hands out: {@code https://host/browse/ABC-42}. */
    private static final Pattern BROWSE_URL = Pattern.compile("^/browse/(?<key>" + KEY + ")(?:[/?#].*)?$");

    private final JsonHttp http;
    private final TrackerProperties config;

    public JiraTracker(JsonHttp http, TrackerProperties config) {
        this.http = http;
        this.config = config;
        if (!config.isUsable()) {
            log.atWarn().setMessage("tracker unusable")
                    .addKeyValue("type", "jira")
                    .addKeyValue("cause", "base-url or token missing")
                    .addKeyValue("effect", "reads stay on the paid headless call")
                    .addKeyValue("fix", "orchestrator.tracker.base-url and .token")
                    .log();
        }
    }

    @Override
    public String displayName() {
        return "Jira";
    }

    @Override
    public boolean supports(String ticketRef) {
        return config.isUsable() && key(ticketRef) != null;
    }

    @Override
    public Optional<TicketFacts> readTicket(String ticketRef) {
        String key = key(ticketRef);
        if (key == null) {
            return Optional.empty();
        }
        // A read that did not come back is reported as an item that does NOT exist, rather than as an absent
        // read: a mistyped key would otherwise fall through to the "nobody read it" path and provision a
        // worktree, an agent and a branch named after a ticket nobody can open.
        return Optional.of(http.get(config.baseUrl() + "/rest/api/2/issue/" + key
                        + "?fields=summary,labels,project", authHeaders())
                .map(issue -> facts(issue, key))
                .orElseGet(() -> new TicketFacts(false, key, null, null, List.of(), null)));
    }

    private TicketFacts facts(JsonNode issue, String requestedKey) {
        JsonNode fields = issue.path("fields");
        // The key comes back from the ISSUE: a moved issue answers under its new one.
        String key = issue.path("key").asString(requestedKey);
        List<String> labels = new ArrayList<>();
        fields.path("labels").forEach(label -> labels.add(label.asString("")));
        return new TicketFacts(true, key, fields.path("summary").asString(""),
                fields.path("project").path("key").asString(""), List.copyOf(labels),
                config.baseUrl() + "/browse/" + key);
    }

    /** The issue key {@code ticketRef} names, or null when this tracker cannot fetch it. */
    private String key(String ticketRef) {
        if (ticketRef == null || ticketRef.isBlank()) {
            return null;
        }
        String ref = ticketRef.strip();
        if (BARE_KEY.matcher(ref).matches()) {
            return ref;
        }
        if (!ref.startsWith(config.baseUrl() + "/")) {
            return null;
        }
        Matcher browse = BROWSE_URL.matcher(ref.substring(config.baseUrl().length()));
        return browse.matches() ? browse.group("key") : null;
    }

    private Map<String, String> authHeaders() {
        if (config.user() == null) {
            return Map.of("Authorization", "Bearer " + config.token());
        }
        String pair = config.user() + ":" + config.token();
        return Map.of("Authorization",
                "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8)));
    }
}
