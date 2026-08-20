package dev.jagt.orchestrator.task;

import dev.jagt.orchestrator.flow.TaskStatus;
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
 * <p>A task works in one or MORE repositories but always in ONE session: {@code repos.get(0)} is where that
 * session runs, and the single-repo accessors answer for it.
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
        // Null = the project's configured baseBranch, so a config change still reaches the task.
        String baseBranch,
        // mrCreatedAt is when jagt stamped the round, not the host's own creation time. Zero = unset.
        long mrCreatedAt,
        // When the HOST says the request was opened — how long the review has been hanging, which no status
        // clock answers. Written by whatever read last saw it; 0 until one did (a model read cannot say).
        long requestOpenedAt,
        long lastPolledAt,
        // When the watchdog last saw a sign of life from an agent it found silent; 0 = not silent.
        long silentSince,
        Boolean autoReview,
        // The host's own wording for the checks, unparsed. Null = never read.
        String pipelineStatus,
        // Null until the first metered call.
        TokenUsage usage,
        // Append-only, oldest first: every status this task actually moved TO, with when.
        List<StatusChange> history
) {

    /** The whole file is rewritten on every update, so the log cannot be unbounded; the OLDEST entries drop. */
    private static final int MAX_HISTORY = 50;

    public TaskState {
        repos = repos == null ? List.of() : List.copyOf(repos);
        history = history == null ? List.of() : List.copyOf(history);
    }

    /**
     * Reads BOTH shapes: the current one with {@code repos}, and older files that carried one repository's
     * fields at the top level.
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
            @JsonProperty("requestOpenedAt") Long requestOpenedAt,
            @JsonProperty("lastPolledAt") long lastPolledAt,
            // Boxed: a state.json written before this field existed omits it, and Jackson 3 refuses to map an
            // absent value onto a primitive — the file would be quarantined as corrupt on the first read.
            @JsonProperty("silentSince") Long silentSince,
            @JsonProperty("autoReview") Boolean autoReview,
            @JsonProperty("pipelineStatus") String pipelineStatus,
            @JsonProperty("usage") TokenUsage usage,
            @JsonProperty("history") List<StatusChange> history) {
        List<TaskRepo> resolved = repos != null && !repos.isEmpty()
                ? repos
                : List.of(new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit));
        return new TaskState(resolved, status, lastActiveTimestamp, message, alias, title, ticketUrl,
                baseBranch, mrCreatedAt, requestOpenedAt == null ? 0 : requestOpenedAt, lastPolledAt,
                silentSince == null ? 0 : silentSince, autoReview, pipelineStatus, usage, history);
    }

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

    /** The primary repo's request only; a multi-repo task has one per repo. */
    @JsonIgnore
    public String mrUrl() {
        return primary().mrUrl();
    }

    @JsonIgnore
    public String deployCommit() {
        return primary().deployCommit();
    }

    @JsonIgnore
    public List<String> projects() {
        return repos.stream().map(TaskRepo::project).filter(p -> p != null && !p.isBlank()).toList();
    }

    @JsonIgnore
    public java.util.Optional<TaskRepo> repo(String project) {
        return repos.stream().filter(r -> r.project() != null && r.project().equals(project)).findFirst();
    }

    /** True when ANY of the repos has a review request open. */
    @JsonIgnore
    public boolean hasReviewRequest() {
        return repos.stream().anyMatch(TaskRepo::hasReviewRequest);
    }

    public TaskState withTicket(String title, String ticketUrl) {
        return toBuilder().title(title).ticketUrl(ticketUrl).build();
    }

    /** A status move — the ONE place history grows. */
    public TaskState withStatus(TaskStatus status, String message) {
        return withStatus(status, message, false);
    }

    /**
     * @param event record even when the status is unchanged — true for something DONE to the task (a second round
     *              shipped onto the same request is a real event), false for a task repeating itself, whose
     *              keep-alives would otherwise drown the real transitions
     */
    public TaskState withStatus(TaskStatus status, String message, boolean event) {
        long now = System.currentTimeMillis();
        List<StatusChange> known = seededHistory();
        boolean record = event || status != this.status;
        return toBuilder().status(status).lastActiveTimestamp(now).message(message).silentSince(0)
                .history(record ? appended(known, new StatusChange(status, now, null)) : known)
                .build();
    }

    /** A NEW review round on one repository's request. The status that follows is not decided here. */
    public TaskState withReviewRound(String project, String reviewRequestUrl) {
        long now = System.currentTimeMillis();
        return relinked(project, reviewRequestUrl).lastActiveTimestamp(now)
                // A new round has new checks: the last one's verdict describes a run that no longer exists.
                .pipelineStatus(null)
                // The polling window is per ROUND, not per request; lastPolledAt=0 means "poll at the next tick".
                .mrCreatedAt(now).lastPolledAt(0)
                .build();
    }

    /** The single-repo form: the round landed on the repo the agent works in. */
    public TaskState withReviewRound(String reviewRequestUrl) {
        return withReviewRound(primary().project(), reviewRequestUrl);
    }

    /**
     * One ship, however many repositories it landed in: every URL goes on its own repo, and the round is recorded
     * ONCE — a history entry per repository would read as several rounds. The recorded round carries the session
     * repo's link, the one a human follows first.
     */
    public TaskState withReviewRound(Map<String, String> urlByProject) {
        if (urlByProject.isEmpty()) {
            return this;
        }
        String primaryUrl = urlByProject.getOrDefault(primary().project(),
                urlByProject.values().iterator().next());
        return withMrUrls(urlByProject).withReviewRound(primary().project(), primaryUrl);
    }

    /** Links each repository to its own request WITHOUT recording a round. */
    public TaskState withMrUrls(Map<String, String> urlByProject) {
        TaskState linked = this;
        for (Map.Entry<String, String> request : urlByProject.entrySet()) {
            linked = linked.withMrUrl(request.getKey(), request.getValue());
        }
        return linked;
    }

    /**
     * A task written before history existed is seeded with its current status at the last activity stamp:
     * otherwise {@link #statusSince()} falls back to a stamp every keep-alive bumps, and an hour-old status
     * reads as brand new.
     */
    private List<StatusChange> seededHistory() {
        if (!history.isEmpty()) {
            return history;
        }
        long since = lastActiveTimestamp > 0 ? lastActiveTimestamp : System.currentTimeMillis();
        return List.of(new StatusChange(status, since, null));
    }

    /**
     * What the watchdog found, so a surface can say a session is blocked without probing per row. Its own wither
     * because silence is not activity: stamping it must not bump {@code lastActiveTimestamp} the way a report
     * does. The other direction needs no caller — any report clears it, since an agent that speaks is alive.
     */
    public TaskState withSilentSince(long silentSince) {
        return toBuilder().silentSince(silentSince).build();
    }

    @JsonIgnore
    public boolean agentIsSilent() {
        return silentSince > 0;
    }

    /** The host's own answer; a read that does not know it (0) must not erase one that did. */
    public TaskState withRequestOpenedAt(long requestOpenedAt) {
        return requestOpenedAt <= 0 ? this : toBuilder().requestOpenedAt(requestOpenedAt).build();
    }

    public TaskState withPipelineStatus(String hostStatus) {
        return toBuilder().pipelineStatus(hostStatus).build();
    }

    /**
     * Stamps who caused the step this task just took — separate from taking it, because the transition is built
     * where the work happens and the asker is known only at the entry point.
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

    /** Whether the oldest steps have been dropped, which makes any total over the log a FLOOR, not the figure. */
    @JsonIgnore
    public boolean historyAtCap() {
        return history.size() >= MAX_HISTORY;
    }

    /**
     * Since when the task has been in its CURRENT status — which is NOT {@code lastActiveTimestamp}: a keep-alive
     * bumps that stamp, so an agent that has been working for an hour looks like it just moved.
     */
    public long statusSince() {
        return history.isEmpty() ? lastActiveTimestamp : history.get(history.size() - 1).at();
    }

    public TaskState touched() {
        return withStatus(status, message);
    }

    /** The merge commit a deploy created in THAT repo; the status move is a separate step. */
    public TaskState withDeployCommit(String project, String deployCommit) {
        return toBuilder().repos(mapRepo(project, repo -> repo.withDeployCommit(deployCommit))).build();
    }

    public TaskState withDeployCommit(String deployCommit) {
        return withDeployCommit(primary().project(), deployCommit);
    }

    public TaskState withMrUrl(String project, String mrUrl) {
        return relinked(project, mrUrl).build();
    }

    /**
     * Points one repository at a request, and DROPS {@code requestOpenedAt} when that changes what is linked: the
     * stamp describes the requests a read saw, so a second request opened on the same task would otherwise read
     * as days old until the next read stamped it again.
     */
    private Builder relinked(String project, String mrUrl) {
        List<TaskRepo> repos = mapRepo(project, repo -> repo.withMrUrl(mrUrl));
        return toBuilder().repos(repos).requestOpenedAt(repos.equals(this.repos) ? requestOpenedAt : 0);
    }

    public TaskState withMrUrl(String mrUrl) {
        return withMrUrl(primary().project(), mrUrl);
    }

    public TaskState withRemoteUrl(String project, String remoteUrl) {
        return toBuilder().repos(mapRepo(project, repo -> repo.withRemoteUrl(remoteUrl))).build();
    }

    public TaskState withMrCreatedAt(long mrCreatedAt) {
        return toBuilder().mrCreatedAt(mrCreatedAt).build();
    }

    public TaskState withLastPolledAt(long lastPolledAt) {
        return toBuilder().lastPolledAt(lastPolledAt).build();
    }

    /** A null project key means the primary repo. */
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

    /** The task's own base branch, or the project default when it has none (null or blank). */
    public String baseBranchOr(String projectDefault) {
        return baseBranch == null || baseBranch.isBlank() ? projectDefault : baseBranch;
    }

    /** An unset (null) per-task flag follows the config default. */
    public boolean autoReviewEnabled(boolean configDefault) {
        return autoReview == null ? configDefault : autoReview;
    }

    public TokenUsage usageOrNone() {
        return usage == null ? TokenUsage.NONE : usage;
    }

    /** Does NOT touch lastActiveTimestamp: metering is not agent activity. */
    public TaskState withUsageAdded(TokenUsage added) {
        return toBuilder().usage(usageOrNone().plus(added)).build();
    }

    public static Builder builder(String project, String worktreePath, TaskStatus status) {
        return new Builder(List.of(TaskRepo.of(project, worktreePath)), status);
    }

    public static Builder builder(List<TaskRepo> repos, TaskStatus status) {
        return new Builder(repos, status);
    }

    private Builder toBuilder() {
        return new Builder(repos, status)
                .lastActiveTimestamp(lastActiveTimestamp).message(message).alias(alias)
                .title(title).ticketUrl(ticketUrl).baseBranch(baseBranch)
                .mrCreatedAt(mrCreatedAt).requestOpenedAt(requestOpenedAt)
                .lastPolledAt(lastPolledAt).silentSince(silentSince)
                .autoReview(autoReview).pipelineStatus(pipelineStatus).usage(usage).history(history);
    }

    /**
     * Missing fields stay unset (null / 0); the repositories and the status are required. The single-repo setters
     * ({@code remoteUrl}, {@code mrUrl}, {@code deployCommit}) write the FIRST repo.
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
        private long requestOpenedAt;
        private long lastPolledAt;
        private long silentSince;
        private Boolean autoReview;
        private String pipelineStatus;
        private TokenUsage usage;
        /** Null means "a brand-new task". */
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

        public Builder requestOpenedAt(long requestOpenedAt) {
            this.requestOpenedAt = requestOpenedAt;
            return this;
        }

        public Builder lastPolledAt(long lastPolledAt) {
            this.lastPolledAt = lastPolledAt;
            return this;
        }

        public Builder silentSince(long silentSince) {
            this.silentSince = silentSince;
            return this;
        }

        public Builder autoReview(Boolean autoReview) {
            this.autoReview = autoReview;
            return this;
        }

        public Builder pipelineStatus(String pipelineStatus) {
            this.pipelineStatus = pipelineStatus;
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
         * A task built from scratch starts its history AT its initial status, so "how long has it sat in NEW" is
         * answerable; a task rebuilt from an existing one carries its own history through.
         */
        public TaskState build() {
            List<StatusChange> log = history != null ? history : List.of(new StatusChange(status,
                    lastActiveTimestamp > 0 ? lastActiveTimestamp : System.currentTimeMillis(), null));
            return new TaskState(repos, status, lastActiveTimestamp, message, alias, title, ticketUrl,
                    baseBranch, mrCreatedAt, requestOpenedAt, lastPolledAt, silentSince, autoReview,
                    pipelineStatus, usage, log);
        }
    }
}
