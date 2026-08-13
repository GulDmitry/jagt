package dev.jagt.orchestrator.codehost;

import dev.jagt.orchestrator.config.CodeHostProperties;
import dev.jagt.orchestrator.model.ReviewFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitLab read over the v4 REST API: approval state, latest pipeline, unresolved discussion notes. Selected by
 * {@code orchestrator.code-host.type=gitlab}.
 *
 * <p>"Approved" is read from the approvals endpoint, i.e. a fact rather than the judgement a model used to be
 * asked for ("approved by a human, not merely mergeable" — the exact call it could get wrong).
 *
 * <p>A PARTIAL read is never reported as a clean review: if the merge request or its discussions cannot be
 * fetched, the whole read fails, because "no unresolved comments" plus a green pipeline ADVANCES the task, and
 * advancing on a failed HTTP call would silently tell the human their review is done. The one exception is the
 * approvals endpoint (absent on some instances/tokens): unreadable there means "not approved", which can only
 * hold a task back, never push it forward.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.code-host.type", havingValue = "gitlab")
public class GitLabCodeHost implements CodeHost {

    private static final Logger log = LoggerFactory.getLogger(GitLabCodeHost.class);
    /** {@code https://host/group/sub/project/-/merge_requests/42} (+ any /diffs, query or fragment tail). */
    private static final Pattern MR_URL = Pattern.compile(
            "^(?<base>https?://[^/]+)/(?<project>.+?)/-/merge_requests/(?<iid>\\d+)(?:[/?#].*)?$");
    private static final int PAGE_SIZE = 100;
    /** A guard against an endless follow-the-pages loop, not a real limit: 1000 discussions is already absurd. */
    private static final int MAX_PAGES = 10;
    /** Bot reviewers write essays; the agent needs the substance, the relay file does not need the whole novel. */
    private static final int MAX_COMMENT_CHARS = 2000;

    private final JsonHttp http;
    private final CodeHostProperties config;

    public GitLabCodeHost(JsonHttp http, CodeHostProperties config) {
        this.http = http;
        this.config = config;
        if (!config.isUsable()) {
            // Loud, because the symptom is silent: every sweep quietly falls back to a PAID headless read.
            log.warn("orchestrator.code-host.type=gitlab but base-url or token is missing — review sweeps keep"
                    + " using the (paid) headless read. Set orchestrator.code-host.base-url and .token.");
        }
    }

    @Override
    public String displayName() {
        return "GitLab";
    }

    @Override
    public boolean supports(String reviewRequestUrl) {
        return config.isUsable()
                && reviewRequestUrl != null
                && reviewRequestUrl.startsWith(config.baseUrl() + "/")
                && MR_URL.matcher(reviewRequestUrl).matches();
    }

    @Override
    public Optional<ReviewFacts> readReview(String reviewRequestUrl) {
        Matcher url = MR_URL.matcher(reviewRequestUrl == null ? "" : reviewRequestUrl);
        if (!url.matches()) {
            return Optional.empty();
        }
        String mrApi = config.baseUrl() + "/api/v4/projects/"
                + URLEncoder.encode(url.group("project"), StandardCharsets.UTF_8)
                + "/merge_requests/" + url.group("iid");
        Optional<JsonNode> detail = get(mrApi);
        if (detail.isEmpty()) {
            return Optional.empty();
        }
        Optional<List<String>> comments = unresolvedComments(mrApi);
        if (comments.isEmpty()) {
            return Optional.empty();
        }
        boolean approved = get(mrApi + "/approvals").map(GitLabCodeHost::isApproved).orElseGet(() -> {
            log.warn("GitLab approvals for {} are unreadable — treating the request as NOT approved", mrApi);
            return false;
        });
        return Optional.of(new ReviewFacts(true, approved, pipelineStatus(detail.get()), comments.get()));
    }

    /** Empty = a page could not be read, which must fail the sweep rather than look like a clean review. */
    private Optional<List<String>> unresolvedComments(String mrApi) {
        List<String> unresolved = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Optional<JsonNode> discussions = get(mrApi + "/discussions?per_page=" + PAGE_SIZE + "&page=" + page);
            if (discussions.isEmpty() || !discussions.get().isArray()) {
                return Optional.empty();
            }
            JsonNode batch = discussions.get();
            batch.forEach(discussion -> discussion.path("notes")
                    .forEach(note -> relayLine(note).ifPresent(unresolved::add)));
            if (batch.size() < PAGE_SIZE) {
                return Optional.of(unresolved);
            }
        }
        // Truncating here would be the same lie as a failed page: the sweep cannot tell a short list from a
        // complete one, and a complete-looking clean list advances the task.
        log.warn("GitLab discussions for {} exceed {} pages — refusing to relay a truncated review round",
                mrApi, MAX_PAGES);
        return Optional.empty();
    }

    /**
     * One unresolved note as the single relay line the brief expects ({@code author (file:line): body}).
     * Empty for anything the human is not waiting on: GitLab's own system notes, plain comments that cannot be
     * resolved at all, and already-resolved threads.
     */
    private static Optional<String> relayLine(JsonNode note) {
        boolean unresolved = note.path("resolvable").asBoolean(false) && !note.path("resolved").asBoolean(false);
        if (!unresolved || note.path("system").asBoolean(false)) {
            return Optional.empty();
        }
        String author = note.path("author").path("username").asString("someone");
        JsonNode position = note.path("position");
        String file = position.path("new_path").asString(position.path("old_path").asString(""));
        long line = position.path("new_line").asLong(position.path("old_line").asLong(0));
        String where = file.isBlank() ? "" : " (" + file + (line > 0 ? ":" + line : "") + ")";
        return Optional.of(author + where + ": " + oneLine(note.path("body").asString("")));
    }

    /** The brief lists one comment per line, so a note's own line breaks would shred that list. */
    private static String oneLine(String body) {
        String flat = body.replaceAll("\\s*\\R\\s*", " ").strip();
        return flat.length() <= MAX_COMMENT_CHARS ? flat : flat.substring(0, MAX_COMMENT_CHARS) + " […]";
    }

    /** {@code approved} is EE-only, so fall back to "somebody is in approved_by" — the CE-safe reading. */
    private static boolean isApproved(JsonNode approvals) {
        return approvals.path("approved").asBoolean(false) || !approvals.path("approved_by").isEmpty();
    }

    private static String pipelineStatus(JsonNode mr) {
        String head = mr.path("head_pipeline").path("status").asString("");
        String any = head.isBlank() ? mr.path("pipeline").path("status").asString("") : head;
        return any.isBlank() ? "none" : any;
    }

    private Optional<JsonNode> get(String url) {
        return http.get(url, Map.of("PRIVATE-TOKEN", config.token()));
    }
}
