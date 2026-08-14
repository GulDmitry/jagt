package dev.jagt.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * One task, as {@code state.json} holds it.
 *
 * <p>A task works in one or MORE repositories ({@link #repos()}): a piece of work can span a PHP service, a
 * Java service and the contract between them, and one agent session changes all of them — the contract only
 * makes sense if both sides move together. What multiplies is worktrees, not sessions. {@code repos.get(0)} is
 * where the agent's session runs, and the single-repo accessors below ({@link #project()},
 * {@link #worktreePath()}, …) answer for it, so everything that legitimately concerns "the task's own place"
 * reads the same as it always did.
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
     * Enough to answer "which steps happened and how long did each take" while keeping `state.json` small — the
     * file is read and rewritten on every single MCP call, so an unbounded log would grow into that hot path.
     * A task that changes status fifty times is pathological, and the OLDEST entries are the ones nobody asks
     * about, so those are what a full history drops.
     */
    private static final int MAX_HISTORY = 50;

    public TaskState {
        repos = repos == null ? List.of() : List.copyOf(repos);
        history = history == null ? List.of() : List.copyOf(history);
    }

    /**
     * Reads BOTH shapes of {@code state.json}: the current one with {@code repos}, and every file written before
     * a task could span repositories, which carried {@code project}/{@code worktreePath}/{@code remoteUrl}/
     * {@code mrUrl}/{@code deployCommit} at the top level. Without this, adding the list would have silently
     * dropped every existing task on the next read — and losing the human's tasks is precisely what the backup
     * machinery in {@code StateService} exists to prevent, so the migration belongs here, not in a note.
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
     * A status move — the ONE place history grows. Records an entry only when the status actually CHANGED: the
     * agent's keep-alive goes through here with its current status (see {@link #touched()}), and logging those
     * would bury the four real transitions of a task under hundreds of identical rows.
     */
    public TaskState withStatus(TaskStatus status, String message) {
        long now = System.currentTimeMillis();
        List<StatusChange> known = seededHistory();
        return toBuilder().status(status).lastActiveTimestamp(now).message(message)
                .history(status == this.status ? known : appended(known, new StatusChange(status, now, null)))
                .build();
    }

    /**
     * A ship landed on a review request: new commits, so a NEW review round — recorded even when the status
     * does not change, which it does not when a round is shipped from CI_POLLING onto the same request. Without
     * this the one question history exists to answer ("how many rounds did this take") is wrong for the path
     * that is actually normal, and the auto-review window would never re-arm for the new pipeline.
     *
     * <p>The URL is stored on the repo it belongs to: a task spanning two repositories has two requests, and
     * putting the second one on top of the first would make the board link to the wrong diff.
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
     * History for a task written BEFORE history existed: seed it with the current status at the last activity
     * stamp. Without this, a legacy task's {@link #statusSince()} falls back to {@code lastActiveTimestamp} —
     * which every keep-alive bumps — so an hour-old status would read as "0m" until its next real transition,
     * the exact lie the field was added to prevent.
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
