package dev.jagt.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskState(
        String project,
        String worktreePath,
        TaskStatus status,
        long lastActiveTimestamp,
        String message,
        String alias,
        String remoteUrl,
        String title,
        String mrUrl,
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
        // The merge commit `deploy` created on the deploy branch — what `revert` reverts. Null until a
        // deploy lands (and for tasks deployed before jagt recorded it, which `revert` then refuses).
        String deployCommit,
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
        history = history == null ? List.of() : List.copyOf(history);
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
                .history(status == this.status ? known : appended(known, new StatusChange(status, now)))
                .build();
    }

    /**
     * A ship landed on the review request: new commits, so a NEW review round — recorded even when the status
     * does not change, which it does not when a round is shipped from CI_POLLING onto the same request. Without
     * this the one question history exists to answer ("how many rounds did this take") is wrong for the path
     * that is actually normal, and the auto-review window would never re-arm for the new pipeline.
     */
    public TaskState withReviewRound(String reviewRequestUrl) {
        long now = System.currentTimeMillis();
        return toBuilder().status(TaskStatus.CI_POLLING).lastActiveTimestamp(now)
                .message("MR: " + reviewRequestUrl)
                .history(appended(seededHistory(), new StatusChange(TaskStatus.CI_POLLING, now)))
                .mrUrl(reviewRequestUrl)
                // The window is per ROUND, not per request: a round shipped days later gets its own polling
                // window, and lastPolledAt=0 makes the next scheduler tick look at it right away.
                .mrCreatedAt(now).lastPolledAt(0)
                .build();
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
        return List.of(new StatusChange(status, since));
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

    /** Records the merge commit a deploy just created; the status move is a separate, explicit step. */
    public TaskState withDeployCommit(String deployCommit) {
        return toBuilder().deployCommit(deployCommit).build();
    }

    public TaskState withMrUrl(String mrUrl) {
        return toBuilder().mrUrl(mrUrl).build();
    }

    public TaskState withMrCreatedAt(long mrCreatedAt) {
        return toBuilder().mrCreatedAt(mrCreatedAt).build();
    }

    public TaskState withLastPolledAt(long lastPolledAt) {
        return toBuilder().lastPolledAt(lastPolledAt).build();
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
        return new Builder(project, worktreePath, status);
    }

    private Builder toBuilder() {
        return new Builder(project, worktreePath, status)
                .lastActiveTimestamp(lastActiveTimestamp).message(message).alias(alias)
                .remoteUrl(remoteUrl).title(title).mrUrl(mrUrl).ticketUrl(ticketUrl).baseBranch(baseBranch)
                .mrCreatedAt(mrCreatedAt).lastPolledAt(lastPolledAt).autoReview(autoReview)
                .usage(usage).deployCommit(deployCommit).history(history);
    }

    /**
     * Mutable builder so callers set only the fields they care about — the record has fourteen fields and
     * a row of positional nulls is exactly the null-soup jagt's config records avoid. Missing fields stay
     * unset (null / 0). project + worktreePath + status are required (the constructor args).
     */
    public static final class Builder {
        private final String project;
        private final String worktreePath;
        private TaskStatus status;
        private long lastActiveTimestamp;
        private String message;
        private String alias;
        private String remoteUrl;
        private String title;
        private String mrUrl;
        private String ticketUrl;
        private String baseBranch;
        private long mrCreatedAt;
        private long lastPolledAt;
        private Boolean autoReview;
        private TokenUsage usage;
        private String deployCommit;
        /** Null means "a brand-new task" — {@link #build()} then seeds it with the initial status. */
        private List<StatusChange> history;

        private Builder(String project, String worktreePath, TaskStatus status) {
            this.project = project;
            this.worktreePath = worktreePath;
            this.status = status;
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
            this.remoteUrl = remoteUrl;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder mrUrl(String mrUrl) {
            this.mrUrl = mrUrl;
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

        public Builder deployCommit(String deployCommit) {
            this.deployCommit = deployCommit;
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
                    lastActiveTimestamp > 0 ? lastActiveTimestamp : System.currentTimeMillis()));
            return new TaskState(project, worktreePath, status, lastActiveTimestamp, message, alias,
                    remoteUrl, title, mrUrl, ticketUrl, baseBranch, mrCreatedAt, lastPolledAt, autoReview,
                    usage, deployCommit, log);
        }
    }
}
