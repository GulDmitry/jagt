package dev.jagt.orchestrator.adapter.codehost;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The READ is one GraphQL query, and that is not a preference: whether a review thread is RESOLVED exists
 * nowhere in the REST API, and a round that cannot tell resolved from open relays every comment it ever saw,
 * every round, forever. One query also makes the all-or-nothing rule trivial — a partial read must never look
 * like a clean review, because "nothing unresolved + green" ADVANCES a task.
 *
 * <p>{@code base-url} is the WEB root ({@code https://github.com}, or an Enterprise host), the same prefix that
 * decides which review URLs this host may claim; the API endpoints are derived from it.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.code-host.type", havingValue = "github")
@Slf4j
public class GitHubCodeHost implements CodeHost {

    /** {@code https://host/owner/repo/pull/42} (+ any /files, query or fragment tail). */
    private static final Pattern PR_URL = Pattern.compile(
            "^https?://[^/]+/(?<owner>[^/]+)/(?<repo>[^/]+)/pull/(?<number>\\d+)(?:[/?#].*)?$");
    private static final int THREADS_PER_PAGE = 100;
    /** A guard against an endless follow-the-cursor loop, not a real limit: 1000 threads is already absurd. */
    private static final int MAX_THREAD_PAGES = 10;
    private static final int MAX_THREAD_COMMENTS = 20;
    /** One entry per reviewer, so this bounds reviewers rather than reviews. */
    private static final int MAX_REVIEWERS = 20;

    private static final String REVIEW_QUERY = """
            query($owner:String!,$repo:String!,$number:Int!,$after:String){
              repository(owner:$owner,name:$repo){
                pullRequest(number:$number){
                  reviewDecision
                  commits(last:1){nodes{commit{statusCheckRollup{state}}}}
                  latestReviews(first:%d){nodes{state body author{login}}}
                  reviewThreads(first:%d,after:$after){
                    pageInfo{hasNextPage endCursor}
                    nodes{isResolved path line
                          comments(first:%d){pageInfo{hasNextPage} nodes{body line author{login}}}}
                  }
                }
              }
            }""".formatted(MAX_REVIEWERS, THREADS_PER_PAGE, MAX_THREAD_COMMENTS);

    private static final String REQUEST_QUERY = """
            query($owner:String!,$repo:String!,$number:Int!){
              repository(owner:$owner,name:$repo){
                pullRequest(number:$number){headRefName baseRefName title}
              }
            }""";

    private final JsonHttp http;
    private final CodeHostProperties config;

    public GitHubCodeHost(JsonHttp http, CodeHostProperties config) {
        this.http = http;
        this.config = config;
        if (!config.isUsable()) {
            log.warn("orchestrator.code-host.type=github but base-url or token is missing — review sweeps keep"
                    + " using the (paid) headless read. Set orchestrator.code-host.base-url and .token.");
        }
    }

    @Override
    public String displayName() {
        return "GitHub";
    }

    @Override
    public boolean supports(String reviewRequestUrl) {
        return config.isUsable()
                && reviewRequestUrl != null
                && reviewRequestUrl.startsWith(config.baseUrl() + "/")
                && PR_URL.matcher(reviewRequestUrl).matches();
    }

    @Override
    public boolean hostsRepository(String gitRemoteUrl) {
        String host = GitRemote.host(gitRemoteUrl);
        return config.isUsable() && host != null && host.equals(GitRemote.host(config.baseUrl()));
    }

    @Override
    public Optional<ReviewFacts> readReview(String reviewRequestUrl) {
        Matcher url = PR_URL.matcher(reviewRequestUrl == null ? "" : reviewRequestUrl);
        if (!url.matches()) {
            return Optional.empty();
        }
        List<String> threadComments = new ArrayList<>();
        JsonNode pullRequest = null;
        String cursor = null;
        for (int page = 1; page <= MAX_THREAD_PAGES; page++) {
            Map<String, Object> variables = variables(url);
            variables.put("after", cursor);
            JsonNode current = pullRequest(REVIEW_QUERY, variables, reviewRequestUrl);
            if (current == null) {
                return Optional.empty();
            }
            // The request-level facts come from the FIRST page; later pages exist only to finish the threads.
            pullRequest = pullRequest == null ? current : pullRequest;
            JsonNode threads = current.path("reviewThreads");
            Optional<List<String>> ofPage = threadComments(threads, reviewRequestUrl);
            if (ofPage.isEmpty()) {
                return Optional.empty();
            }
            threadComments.addAll(ofPage.get());
            if (!threads.path("pageInfo").path("hasNextPage").asBoolean(false)) {
                return Optional.of(facts(pullRequest, threadComments));
            }
            cursor = threads.path("pageInfo").path("endCursor").asString("");
            if (cursor.isBlank()) {
                log.warn("GitHub reports more review threads for {} but no cursor to read them with",
                        reviewRequestUrl);
                return Optional.empty();
            }
        }
        // Truncating would be the same lie as a failed page: the sweep cannot tell a short list from a
        // complete one, and a complete-looking clean list advances the task.
        log.warn("GitHub review of {} exceeds {} thread pages — refusing to relay a truncated round",
                reviewRequestUrl, MAX_THREAD_PAGES);
        return Optional.empty();
    }

    @Override
    public Optional<MergeRequestFacts> readRequest(String reviewRequestUrl) {
        Matcher url = PR_URL.matcher(reviewRequestUrl == null ? "" : reviewRequestUrl);
        if (!url.matches()) {
            return Optional.empty();
        }
        JsonNode pullRequest = pullRequest(REQUEST_QUERY, variables(url), reviewRequestUrl);
        return pullRequest == null ? Optional.empty() : Optional.of(new MergeRequestFacts(true,
                pullRequest.path("headRefName").asString(""),
                pullRequest.path("baseRefName").asString(""),
                pullRequest.path("title").asString("")));
    }

    /** Null when nothing trustworthy came back. */
    private JsonNode pullRequest(String query, Map<String, Object> variables, String reviewRequestUrl) {
        Optional<JsonNode> answer = http.post(graphqlApi(), authHeaders(),
                Map.of("query", query, "variables", variables));
        if (answer.isEmpty()) {
            return null;
        }
        // GraphQL answers a broken query, a missing scope or a deleted repository with HTTP 200 and an errors
        // array, so the status code alone would read a failed read as an empty one.
        if (!answer.get().path("errors").isEmpty()) {
            log.warn("GitHub refused the query for {}: {}", reviewRequestUrl,
                    answer.get().path("errors").toString());
            return null;
        }
        JsonNode pullRequest = answer.get().path("data").path("repository").path("pullRequest");
        return pullRequest.isObject() ? pullRequest : null;
    }

    /**
     * The review-level lines come FIRST because they frame the round — and a CHANGES_REQUESTED decision always
     * leaves at least one line, whatever the reviewer typed: with an empty list and a green rollup the sweep
     * would advance the task to REVIEWED while the host is blocking the merge.
     */
    private static ReviewFacts facts(JsonNode pullRequest, List<String> threadComments) {
        List<String> comments = new ArrayList<>(reviewComments(pullRequest));
        comments.addAll(threadComments);
        if (comments.isEmpty() && "CHANGES_REQUESTED".equals(pullRequest.path("reviewDecision").asString(""))) {
            comments.add("the reviewer: requested changes — open the review request, it carries no comment");
        }
        return new ReviewFacts(true, approved(pullRequest), checkStatus(pullRequest), List.copyOf(comments));
    }

    /**
     * The bodies of the reviews themselves, which is where a GitHub reviewer usually writes what they want —
     * inline threads are optional, and a round read from threads alone can miss the whole request.
     */
    private static List<String> reviewComments(JsonNode pullRequest) {
        List<String> lines = new ArrayList<>();
        for (JsonNode review : pullRequest.path("latestReviews").path("nodes")) {
            String state = review.path("state").asString("");
            if (!"CHANGES_REQUESTED".equals(state) && !"COMMENTED".equals(state)) {
                continue;
            }
            String author = review.path("author").path("login").asString("someone");
            String body = review.path("body").asString("");
            if (!body.isBlank()) {
                lines.add(RelayLine.of(author, "", 0, body));
            } else if ("CHANGES_REQUESTED".equals(state)) {
                lines.add(RelayLine.of(author, "", 0, "requested changes without writing a comment"));
            }
        }
        return lines;
    }

    /**
     * {@code reviewDecision} is only populated where the repository REQUIRES a review, so an unprotected
     * repository answers null however many people clicked Approve — the reviewers' own latest states are the
     * fallback, and one of them still asking for changes outweighs the rest.
     */
    private static boolean approved(JsonNode pullRequest) {
        String decision = pullRequest.path("reviewDecision").asString("");
        if (!decision.isBlank()) {
            return "APPROVED".equals(decision);
        }
        boolean anyApproval = false;
        for (JsonNode review : pullRequest.path("latestReviews").path("nodes")) {
            String state = review.path("state").asString("");
            if ("CHANGES_REQUESTED".equals(state)) {
                return false;
            }
            anyApproval |= "APPROVED".equals(state);
        }
        return anyApproval;
    }

    /** Empty = the round could only be relayed in part, which must fail the sweep rather than look clean. */
    private Optional<List<String>> threadComments(JsonNode reviewThreads, String reviewRequestUrl) {
        if (!reviewThreads.path("nodes").isArray()) {
            log.warn("GitHub returned no thread list for {} — refusing to read it as a clean review",
                    reviewRequestUrl);
            return Optional.empty();
        }
        List<String> unresolved = new ArrayList<>();
        for (JsonNode thread : reviewThreads.path("nodes")) {
            if (thread.path("isResolved").asBoolean(false)) {
                continue;
            }
            JsonNode comments = thread.path("comments");
            if (!comments.path("nodes").isArray()
                    || comments.path("pageInfo").path("hasNextPage").asBoolean(false)) {
                log.warn("A GitHub thread in {} is longer than one read carries — refusing to relay a"
                        + " truncated round", reviewRequestUrl);
                return Optional.empty();
            }
            // Every comment of an open thread, not just the first: the ask often moves on ("no, do X instead"),
            // and the agent decides per COMMENT whether it is right.
            String file = thread.path("path").asString("");
            long threadLine = thread.path("line").asLong(0);
            for (JsonNode comment : comments.path("nodes")) {
                unresolved.add(RelayLine.of(comment.path("author").path("login").asString("someone"),
                        file, comment.path("line").asLong(threadLine), comment.path("body").asString("")));
            }
        }
        return Optional.of(unresolved);
    }

    /**
     * ERROR is a failure — a check that could not run is not a check that is still running, and calling it
     * pending would leave the task waiting forever.
     */
    private static String checkStatus(JsonNode pullRequest) {
        JsonNode rollup = pullRequest.path("commits").path("nodes").path(0).path("commit")
                .path("statusCheckRollup");
        if (rollup.isMissingNode() || rollup.isNull()) {
            return "none";
        }
        String state = rollup.path("state").asString("");
        return switch (state) {
            case "SUCCESS" -> "success";
            case "FAILURE", "ERROR" -> "failed";
            case "PENDING", "EXPECTED" -> "pending";
            case "" -> "none";
            default -> state.toLowerCase();
        };
    }

    /**
     * {@code removeSourceBranch} and {@code squash} are not written: on this host they are REPOSITORY settings
     * chosen at merge time, not properties of the request — and a {@link CodeHost} configures no repository. An
     * already-open request is never retitled either: every review round ships again, and the human may have
     * edited the title.
     */
    @Override
    public Optional<MergeRequestRef> createOrUpdateMergeRequest(MergeRequestSpec spec) {
        String projectPath = GitRemote.projectPath(spec.remoteUrl());
        if (!hostsRepository(spec.remoteUrl()) || projectPath == null || !projectPath.contains("/")) {
            return Optional.empty();
        }
        String owner = projectPath.substring(0, projectPath.indexOf('/'));
        String pulls = restApi() + "/repos/" + projectPath + "/pulls";
        Optional<JsonNode> open = http.get(pulls + "?state=open"
                + "&head=" + owner + ":" + query(spec.sourceBranch())
                + "&base=" + query(spec.targetBranch()), authHeaders());
        if (open.isEmpty() || !open.get().isArray()) {
            return Optional.empty();
        }
        if (!open.get().isEmpty()) {
            return Optional.of(new MergeRequestRef(open.get().get(0).path("html_url").asString(""), false));
        }
        return http.post(pulls, authHeaders(), Map.of(
                        "title", spec.title(),
                        "head", spec.sourceBranch(),
                        "base", spec.targetBranch()))
                .map(created -> new MergeRequestRef(created.path("html_url").asString(""), true));
    }

    /** Mutable: only the paged read has a cursor to add. */
    private static Map<String, Object> variables(Matcher url) {
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("owner", url.group("owner"));
        variables.put("repo", repoName(url));
        variables.put("number", Integer.parseInt(url.group("number")));
        return variables;
    }

    /** github.com serves its API from api.github.com; an Enterprise host serves it under its own root. */
    private String restApi() {
        return isDotCom() ? "https://api.github.com" : config.baseUrl() + "/api/v3";
    }

    private String graphqlApi() {
        return isDotCom() ? "https://api.github.com/graphql" : config.baseUrl() + "/api/graphql";
    }

    private boolean isDotCom() {
        return "github.com".equals(GitRemote.host(config.baseUrl()));
    }

    /** A repository name in a URL may carry the {@code .git} suffix someone pasted along with it. */
    private static String repoName(Matcher url) {
        String repo = url.group("repo");
        return repo.endsWith(".git") ? repo.substring(0, repo.length() - 4) : repo;
    }

    /** A branch name may carry slashes ({@code release/1.2}), a '+' or a space, which a raw value would lose. */
    private static String query(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Map<String, String> authHeaders() {
        return Map.of("Authorization", "Bearer " + config.token(),
                "Accept", "application/vnd.github+json");
    }
}
