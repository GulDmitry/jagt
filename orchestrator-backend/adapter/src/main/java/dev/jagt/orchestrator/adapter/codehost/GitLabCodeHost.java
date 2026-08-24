package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.adapter.HostStamp;
import dev.jagt.orchestrator.port.CodeHost;
import dev.jagt.orchestrator.config.CodeHostProperties;
import dev.jagt.orchestrator.port.JsonHttp;
import dev.jagt.orchestrator.task.GitRemote;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.MergeRequestRef;
import dev.jagt.orchestrator.task.MergeRequestSpec;
import dev.jagt.orchestrator.task.ReviewFacts;
import lombok.extern.slf4j.Slf4j;
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
 * A PARTIAL read must never look like a clean review: an unfetchable request or discussion list fails the
 * whole read, because "nothing unresolved + green" ADVANCES a task. The one exception is the approvals endpoint
 * (absent on some instances): unreadable there means "not approved", which can only hold a task back.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.code-host.type", havingValue = "gitlab")
@Slf4j
public class GitLabCodeHost implements CodeHost {

    /** {@code https://host/group/sub/project/-/merge_requests/42} (+ any /diffs, query or fragment tail). */
    private static final Pattern MR_URL = Pattern.compile(
            "^(?<base>https?://[^/]+)/(?<project>.+?)/-/merge_requests/(?<iid>\\d+)(?:[/?#].*)?$");
    private static final int PAGE_SIZE = 100;
    /** A guard against an endless follow-the-pages loop, not a real limit: 1000 discussions is already absurd. */
    private static final int MAX_PAGES = 10;

    private final JsonHttp http;
    private final CodeHostProperties config;

    public GitLabCodeHost(JsonHttp http, CodeHostProperties config) {
        this.http = http;
        this.config = config;
        if (!config.isUsable()) {
            log.atWarn().setMessage("code host unusable")
                    .addKeyValue("type", "gitlab")
                    .addKeyValue("cause", "base-url or token missing")
                    .addKeyValue("effect", "reads stay on the paid headless call")
                    .addKeyValue("fix", "orchestrator.code-host.base-url and .token")
                    .log();
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
    public boolean hostsRepository(String gitRemoteUrl) {
        String host = GitRemote.host(gitRemoteUrl);
        return config.isUsable() && host != null && host.equals(GitRemote.host(config.baseUrl()));
    }

    @Override
    public Optional<MergeRequestRef> createOrUpdateMergeRequest(MergeRequestSpec spec) {
        String projectPath = GitRemote.projectPath(spec.remoteUrl());
        if (!hostsRepository(spec.remoteUrl()) || projectPath == null) {
            return Optional.empty();
        }
        String mergeRequests = config.baseUrl() + "/api/v4/projects/"
                + URLEncoder.encode(projectPath, StandardCharsets.UTF_8) + "/merge_requests";
        Optional<JsonNode> open = get(mergeRequests
                + "?state=opened&source_branch=" + query(spec.sourceBranch())
                + "&target_branch=" + query(spec.targetBranch()));
        if (open.isEmpty() || !open.get().isArray()) {
            return Optional.empty();
        }
        if (open.get().isEmpty()) {
            return http.post(mergeRequests, authHeaders(), Map.of(
                            "source_branch", spec.sourceBranch(),
                            "target_branch", spec.targetBranch(),
                            "title", spec.title(),
                            "remove_source_branch", spec.removeSourceBranch(),
                            "squash", spec.squash()))
                    .map(created -> new MergeRequestRef(created.path("web_url").asString(""), true));
        }
        return Optional.of(alignExisting(mergeRequests, open.get().get(0), spec));
    }

    /**
     * An already-open request is NEVER retitled: a ship runs again on every review round, the title came from
     * the first one, and the human may well have edited it since. A failed flag update still reports the
     * request, because it EXISTS: answering "no merge request" over a cosmetic flag would send the caller off
     * to create a second one.
     */
    private MergeRequestRef alignExisting(String mergeRequests, JsonNode existing, MergeRequestSpec spec) {
        MergeRequestRef found = new MergeRequestRef(existing.path("web_url").asString(""), false);
        boolean sameFlags = existing.path("force_remove_source_branch").asBoolean(false) == spec.removeSourceBranch()
                && existing.path("squash").asBoolean(false) == spec.squash();
        if (sameFlags) {
            return found;
        }
        String url = mergeRequests + "/" + existing.path("iid").asLong(0);
        Optional<JsonNode> updated = http.put(url, authHeaders(), Map.of(
                "remove_source_branch", spec.removeSourceBranch(), "squash", spec.squash()));
        if (updated.isEmpty()) {
            log.atWarn().setMessage("merge flags not aligned")
                    .addKeyValue("url", url)
                    .addKeyValue("effect", "request unaffected")
                    .log();
        }
        return found;
    }

    @Override
    public Optional<ReviewFacts> readReview(String reviewRequestUrl) {
        Matcher url = MR_URL.matcher(reviewRequestUrl == null ? "" : reviewRequestUrl);
        if (!url.matches()) {
            return Optional.empty();
        }
        String mrApi = mrApi(url);
        Optional<JsonNode> detail = get(mrApi);
        if (detail.isEmpty()) {
            return Optional.empty();
        }
        Optional<List<String>> comments = unresolvedComments(mrApi);
        if (comments.isEmpty()) {
            return Optional.empty();
        }
        boolean approved = get(mrApi + "/approvals").map(GitLabCodeHost::isApproved).orElseGet(() -> {
            log.atWarn().setMessage("gitlab approvals unreadable")
                    .addKeyValue("api", mrApi)
                    .addKeyValue("effect", "treated as not approved")
                    .log();
            return false;
        });
        return Optional.of(new ReviewFacts(true, approved, pipelineStatus(detail.get()), comments.get(),
                HostStamp.epochMillis(detail.get().path("created_at").asString(""))));
    }

    @Override
    public Optional<MergeRequestFacts> readRequest(String reviewRequestUrl) {
        Matcher url = MR_URL.matcher(reviewRequestUrl == null ? "" : reviewRequestUrl);
        if (!url.matches()) {
            return Optional.empty();
        }
        return get(mrApi(url)).map(detail -> new MergeRequestFacts(true,
                detail.path("source_branch").asString(""),
                detail.path("target_branch").asString(""),
                detail.path("title").asString("")));
    }

    private String mrApi(Matcher url) {
        return config.baseUrl() + "/api/v4/projects/"
                + URLEncoder.encode(url.group("project"), StandardCharsets.UTF_8)
                + "/merge_requests/" + url.group("iid");
    }

    /** Empty = a page could not be read; an empty list = nothing is unresolved. */
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
        log.atWarn().setMessage("gitlab discussions too long")
                .addKeyValue("api", mrApi)
                .addKeyValue("maxPages", MAX_PAGES)
                .addKeyValue("effect", "round not relayed")
                .log();
        return Optional.empty();
    }

    /**
     * Empty for anything the human is not waiting on: GitLab's own system notes, plain comments that cannot be
     * resolved at all, and already-resolved threads.
     */
    private static Optional<String> relayLine(JsonNode note) {
        boolean unresolved = note.path("resolvable").asBoolean(false) && !note.path("resolved").asBoolean(false);
        if (!unresolved || note.path("system").asBoolean(false)) {
            return Optional.empty();
        }
        JsonNode position = note.path("position");
        return Optional.of(RelayLine.of(
                note.path("author").path("username").asString("someone"),
                position.path("new_path").asString(position.path("old_path").asString("")),
                position.path("new_line").asLong(position.path("old_line").asLong(0)),
                note.path("body").asString("")));
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
        return http.get(url, authHeaders());
    }

    private Map<String, String> authHeaders() {
        return Map.of("PRIVATE-TOKEN", config.token());
    }

    /** Branch names may carry slashes ({@code release/1.2}), which a raw query parameter would cut in two. */
    private static String query(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
