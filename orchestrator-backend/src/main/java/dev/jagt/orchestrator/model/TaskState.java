package dev.jagt.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One task, as {@code state.json} holds it.
 *
 * <p>A task works in one or MORE repositories ({@link #repos()}) but always in ONE session: what multiplies is
 * worktrees. {@code repos.get(0)} is where that session runs, and the single-repo accessors answer for it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskState(
        List<TaskRepo> repos,
        TaskStatus status,
        long lastActiveTimestamp,
        String message,
        String alias,
        String title,
        String ticketUrl,
        // The branch this task was cut from and whose review request it targets, when the human named one at
        // `do` time. Null = the project's configured baseBranch, so a config change still reaches the task.
        String baseBranch,
        // Auto-review window: when the MR was first linked (window start), the last auto-poll, and the
        // per-task on/off (null = follow the config default). Zero = unset.
        long mrCreatedAt,
        long lastPolledAt,
        Boolean autoReview,
        // Master-side model spend on this task (headless assistant calls); null until the first one.
        TokenUsage usage,
        // Append-only, oldest first: every status this task actually moved TO, with when. Capped, see below.
        List<StatusChange> history
) {

    /**
     * The file is rewritten on every MCP call, so the log cannot be unbounded. A full history drops its OLDEST
     * entries — the ones nobody asks about.
     */
    private static final int MAX_HISTORY = 50;

    public TaskState {
        repos = repos == null ? List.of() : List.copyOf(repos);
        history = history == null ? List.of() : List.copyOf(history);
    }

    /**
     * Reads BOTH shapes of {@code state.json}: the current one with {@code repos}, and the older files that
     * carried {@code project}/{@code worktreePath}/{@code remoteUrl}/{@code mrUrl}/{@code deployCommit} at the
     * top level. Without it, the next read of an existing file would silently drop every task in it.
     */
    @JsonCreator
    static TaskState fromJson(
            @JsonProperty("repos") List<TaskRepo> repos,
            @JsonProperty("project") String project,
            @JsonProperty("worktreePath") String worktreePath,
            @JsonProperty("remoteUrl") String remoteUrl,
            @JsonProperty("mrUrl") String mrUrl,
            @JsonProperty("deployCommit") String deployCommit,
            @JsonProperty("status") TaskStatus status,
            @JsonProperty("lastActiveTimestamp") long lastActiveTimestamp,
            @JsonProperty("message") String message,
            @JsonProperty("alias") String alias,
            @JsonProperty("title") String title,
            @JsonProperty("ticketUrl") String ticketUrl,
            @JsonProperty("baseBranch") String baseBranch,
            @JsonProperty("mrCreatedAt") long mrCreatedAt,
            @JsonProperty("lastPolledAt") long lastPolledAt,
            @JsonProperty("autoReview") Boolean autoReview,
            @JsonProperty("usage") TokenUsage usage,
            @JsonProperty("history") List<StatusChange> history) {
        List<TaskRepo> resolved = repos != null && !repos.isEmpty()
                ? repos
                : List.of(new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit));
        return new TaskState(resolved, status, lastActiveTimestamp, message, alias, title, ticketUrl,
                baseBranch, mrCreatedAt, lastPolledAt, autoReview, usage, history);
    }

    /** Where the agent's session runs, and what the single-repo accessors answer for. */
    @JsonIgnore
    public TaskRepo primary() {
        return repos.isEmpty() ? new TaskRepo(null, null, null, null, null) : repos.get(0);
    }

    @JsonIgnore
    public String project() {
        return primary().project();
    }

    @JsonIgnore
    public String worktreePath() {
        return primary().worktreePath();
    }

    @JsonIgnore
    public String remoteUrl() {
        return primary().remoteUrl();
    }

    /** The primary repo's review request. A multi-repo task has one per repo — see {@link #repos()}. */
    @JsonIgnore
    public String mrUrl() {
        return primary().mrUrl();
    }

    @JsonIgnore
    public String deployCommit() {
        return primary().deployCommit();
    }

    /** Every project this task touches, in order; one entry for the ordinary single-repo task. */
    @JsonIgnore
    public List<String> projects() {
        return repos.stream().map(TaskRepo::project).filter(p -> p != null && !p.isBlank()).toList();
    }

    /** The repo for a project key, or empty — a caller acting per repository must not guess which one it got. */
    @JsonIgnore
    public java.util.Optional<TaskRepo> repo(String project) {
        return repos.stream().filter(r -> r.project() != null && r.project().equals(project)).findFirst();
    }

    /** True when ANY repo has a review request open: the question every "is there something to sweep" asks. */
    @JsonIgnore
    public boolean hasReviewRequest() {
        return repos.stream().anyMatch(TaskRepo::hasReviewRequest);
    }

    public TaskState withTicket(String title, String ticketUrl) {
        return toBuilder().title(title).ticketUrl(ticketUrl).build();
    }

    /**
     * A status move — the ONE place history grows, and only when the status actually CHANGED: the keep-alive
     * comes through here too, and four real transitions must not drown in hundreds of identical rows.
     */
    public TaskState withStatus(TaskStatus status, String message) {
        long now = System.currentTimeMillis();
        List<StatusChange> known = seededHistory();
        return toBuilder().status(status).lastActiveTimestamp(now).message(message)
                .history(status == this.status ? known : appended(known, new StatusChange(status, now, null)))
                .build();
    }

    /**
     * A ship landed: a NEW review round, recorded even when the status does not change — which it does not for
     * a round shipped from CI_POLLING onto the same request, the normal path.
     *
     * <p>The URL goes on the repo it belongs to: a task spanning two would otherwise link to the wrong diff.
     */
    public TaskState withReviewRound(String project, String reviewRequestUrl) {
        long now = System.currentTimeMillis();
        return toBuilder().status(TaskStatus.CI_POLLING).lastActiveTimestamp(now)
                .message("MR: " + reviewRequestUrl)
                .history(appended(seededHistory(), new StatusChange(TaskStatus.CI_POLLING, now, null)))
                .repos(mapRepo(project, repo -> repo.withMrUrl(reviewRequestUrl)))
                // The window is per ROUND, not per request: a round shipped days later gets its own polling
                // window, and lastPolledAt=0 makes the next scheduler tick look at it right away.
                .mrCreatedAt(now).lastPolledAt(0)
                .build();
    }

    /** The single-repo form: the round landed on the repo the agent works in. */
    public TaskState withReviewRound(String reviewRequestUrl) {
        return withReviewRound(primary().project(), reviewRequestUrl);
    }

    /**
     * One ship, however many repositories it landed in: every URL goes on its own repo, and the round is
     * recorded ONCE — a history entry per repository would read as several rounds.
     *
     * <p>The status message carries the session repo's link, the one a human follows first; the rest are on the
     * repos, which is where a surface reads them from anyway.
     */
    public TaskState withReviewRound(Map<String, String> urlByProject) {
        if (urlByProject.isEmpty()) {
            return this;
        }
        String primaryUrl = urlByProject.getOrDefault(primary().project(),
                urlByProject.values().iterator().next());
        return withMrUrls(urlByProject).withReviewRound(primary().project(), primaryUrl);
    }

    /**
     * Links each repository to its own request WITHOUT recording a round — what a ship that failed part way
     * still knows for certain: those requests exist, whatever the task's status ends up saying.
     */
    public TaskState withMrUrls(Map<String, String> urlByProject) {
        TaskState linked = this;
        for (Map.Entry<String, String> request : urlByProject.entrySet()) {
            linked = linked.withMrUrl(request.getKey(), request.getValue());
        }
        return linked;
    }

    /**
     * A task written before history existed is seeded with its current status at the last activity stamp:
     * otherwise {@link #statusSince()} falls back to a field every keep-alive bumps, and an hour-old status
     * reads as "0m".
     */
    private List<StatusChange> seededHistory() {
        if (!history.isEmpty()) {
            return history;
        }
        long since = lastActiveTimestamp > 0 ? lastActiveTimestamp : System.currentTimeMillis();
        return List.of(new StatusChange(status, since, null));
    }

    /**
     * Names who caused the step this task just took. Separate from taking the step because the two are known in
     * different places: the transition is built where the work happens, the asker only at the entry point.
     */
    public TaskState withLastChangeOrigin(ActionOrigin origin) {
        if (history.isEmpty()) {
            return this;
        }
        List<StatusChange> stamped = new ArrayList<>(history);
        stamped.set(stamped.size() - 1, stamped.getLast().by(origin));
        return toBuilder().history(List.copyOf(stamped)).build();
    }

    private static List<StatusChange> appended(List<StatusChange> history, StatusChange change) {
        List<StatusChange> grown = new ArrayList<>(history);
        grown.add(change);
        return grown.size() <= MAX_HISTORY
                ? List.copyOf(grown)
                : List.copyOf(grown.subList(grown.size() - MAX_HISTORY, grown.size()));
    }

    /**
     * Since when the task has been in its CURRENT status — which is NOT {@code lastActiveTimestamp}: a
     * keep-alive bumps that stamp, so an agent that has been working for an hour looks like it just moved.
     * Falls back to the activity stamp for a task written before history existed.
     */
    /**
     * Whether the oldest steps have been dropped, which makes anything counted over the whole log — how many
     * times it went out for review, how long ago it started — a FLOOR rather than the figure.
     */
    @JsonIgnore
    public boolean historyAtCap() {
        return history.size() >= MAX_HISTORY;
    }

    public long statusSince() {
        return history.isEmpty() ? lastActiveTimestamp : history.get(history.size() - 1).at();
    }

    public TaskState touched() {
        return withStatus(status, message);
    }

    /** Records the merge commit a deploy just created IN THAT REPO; the status move is a separate step. */
    public TaskState withDeployCommit(String project, String deployCommit) {
        return toBuilder().repos(mapRepo(project, repo -> repo.withDeployCommit(deployCommit))).build();
    }

    public TaskState withDeployCommit(String deployCommit) {
        return withDeployCommit(primary().project(), deployCommit);
    }

    public TaskState withMrUrl(String project, String mrUrl) {
        return toBuilder().repos(mapRepo(project, repo -> repo.withMrUrl(mrUrl))).build();
    }

    public TaskState withMrUrl(String mrUrl) {
        return withMrUrl(primary().project(), mrUrl);
    }

    /** Records the remote of a repo, learned when its worktree was created. */
    public TaskState withRemoteUrl(String project, String remoteUrl) {
        return toBuilder().repos(mapRepo(project, repo -> repo.withRemoteUrl(remoteUrl))).build();
    }

    public TaskState withMrCreatedAt(long mrCreatedAt) {
        return toBuilder().mrCreatedAt(mrCreatedAt).build();
    }

    public TaskState withLastPolledAt(long lastPolledAt) {
        return toBuilder().lastPolledAt(lastPolledAt).build();
    }

    /** Applies a change to ONE repo by project key, leaving the others exactly as they were. */
    private List<TaskRepo> mapRepo(String project, java.util.function.UnaryOperator<TaskRepo> change) {
        List<TaskRepo> updated = new ArrayList<>(repos.size());
        for (TaskRepo repo : repos) {
            boolean matches = project == null
                    ? repo.equals(primary())
                    : project.equals(repo.project());
            updated.add(matches ? change.apply(repo) : repo);
        }
        return List.copyOf(updated);
    }

    /**
     * The branch this task branched off and merges back into — its own override if the human named one at
     * {@code do} time, else the project default the caller passes in. ONE answer for the worktree's base, the
     * review request's target and the {@code ide … diff} snapshot; they cannot drift apart.
     */
    public String baseBranchOr(String projectDefault) {
        return baseBranch == null || baseBranch.isBlank() ? projectDefault : baseBranch;
    }

    /** True unless the task explicitly opted out; a null (legacy/unset) follows the config default. */
    public boolean autoReviewEnabled(boolean configDefault) {
        return autoReview == null ? configDefault : autoReview;
    }

    /** Spend so far, never null — an untouched (or legacy) task has cost nothing. */
    public TokenUsage usageOrNone() {
        return usage == null ? TokenUsage.NONE : usage;
    }

    /** Adds one call's cost. Does NOT touch lastActiveTimestamp: metering is not agent activity. */
    public TaskState withUsageAdded(TokenUsage added) {
        return toBuilder().usage(usageOrNone().plus(added)).build();
    }

    /** A required-fields entry point; optional fields default to unset and are layered on with setters. */
    public static Builder builder(String project, String worktreePath, TaskStatus status) {
        return new Builder(List.of(TaskRepo.of(project, worktreePath)), status);
    }

    /** The multi-repo entry point: every repository the task works in, the agent's own one first. */
    public static Builder builder(List<TaskRepo> repos, TaskStatus status) {
        return new Builder(repos, status);
    }

    private Builder toBuilder() {
        return new Builder(repos, status)
                .lastActiveTimestamp(lastActiveTimestamp).message(message).alias(alias)
                .title(title).ticketUrl(ticketUrl).baseBranch(baseBranch)
                .mrCreatedAt(mrCreatedAt).lastPolledAt(lastPolledAt).autoReview(autoReview)
                .usage(usage).history(history);
    }

    /**
     * Mutable builder so callers set only the fields they care about — a row of positional nulls is exactly the
     * null-soup jagt's config records avoid. Missing fields stay unset (null / 0). The repositories and the
     * status are required. The single-repo setters ({@code remoteUrl}, {@code mrUrl}, {@code deployCommit})
     * write the FIRST repo, which is what a single-repo caller means by them.
     */
    public static final class Builder {
        private List<TaskRepo> repos;
        private TaskStatus status;
        private long lastActiveTimestamp;
        private String message;
        private String alias;
        private String title;
        private String ticketUrl;
        private String baseBranch;
        private long mrCreatedAt;
        private long lastPolledAt;
        private Boolean autoReview;
        private TokenUsage usage;
        /** Null means "a brand-new task" — {@link #build()} then seeds it with the initial status. */
        private List<StatusChange> history;

        private Builder(List<TaskRepo> repos, TaskStatus status) {
            this.repos = repos == null || repos.isEmpty() ? List.of(TaskRepo.of(null, null)) : List.copyOf(repos);
            this.status = status;
        }

        public Builder repos(List<TaskRepo> repos) {
            this.repos = List.copyOf(repos);
            return this;
        }

        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder lastActiveTimestamp(long lastActiveTimestamp) {
            this.lastActiveTimestamp = lastActiveTimestamp;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder remoteUrl(String remoteUrl) {
            return firstRepo(repo -> repo.withRemoteUrl(remoteUrl));
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder mrUrl(String mrUrl) {
            return firstRepo(repo -> repo.withMrUrl(mrUrl));
        }

        public Builder deployCommit(String deployCommit) {
            return firstRepo(repo -> repo.withDeployCommit(deployCommit));
        }

        private Builder firstRepo(java.util.function.UnaryOperator<TaskRepo> change) {
            List<TaskRepo> updated = new ArrayList<>(repos);
            updated.set(0, change.apply(updated.get(0)));
            this.repos = List.copyOf(updated);
            return this;
        }

        public Builder ticketUrl(String ticketUrl) {
            this.ticketUrl = ticketUrl;
            return this;
        }

        public Builder baseBranch(String baseBranch) {
            this.baseBranch = baseBranch;
            return this;
        }

        public Builder mrCreatedAt(long mrCreatedAt) {
            this.mrCreatedAt = mrCreatedAt;
            return this;
        }

        public Builder lastPolledAt(long lastPolledAt) {
            this.lastPolledAt = lastPolledAt;
            return this;
        }

        public Builder autoReview(Boolean autoReview) {
            this.autoReview = autoReview;
            return this;
        }

        public Builder usage(TokenUsage usage) {
            this.usage = usage;
            return this;
        }

        public Builder history(List<StatusChange> history) {
            this.history = history;
            return this;
        }

        /**
         * A task built from scratch starts its history AT its initial status — otherwise the first entry would
         * be the second thing that ever happened to it, and "how long did it sit in NEW" would need a
         * timestamp nobody kept. A task rebuilt from an existing one carries its own history through.
         */
        public TaskState build() {
            List<StatusChange> log = history != null ? history : List.of(new StatusChange(status,
                    lastActiveTimestamp > 0 ? lastActiveTimestamp : System.currentTimeMillis(), null));
            return new TaskState(repos, status, lastActiveTimestamp, message, alias, title, ticketUrl,
                    baseBranch, mrCreatedAt, lastPolledAt, autoReview, usage, log);
        }
    }
}
