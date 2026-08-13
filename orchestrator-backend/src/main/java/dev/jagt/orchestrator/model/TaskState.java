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
        history = history == null ? List.of() : List.copyOf(history);
    }

    /**
     * A status move — the ONE place history grows. Records an entry only when the status actually CHANGED: the
     * agent's keep-alive goes through here with its current status (see {@link #touched()}), and logging those
     * would bury the four real transitions of a task under hundreds of identical rows.
     */
    public TaskState withStatus(TaskStatus status, String message) {
        long now = System.currentTimeMillis();
        return toBuilder().status(status).lastActiveTimestamp(now).message(message)
                .history(status == this.status ? history : appended(history, new StatusChange(status, now)))
                .build();
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

    public TaskState withMrUrl(String mrUrl) {
        return toBuilder().mrUrl(mrUrl).build();
    }

    public TaskState withMrCreatedAt(long mrCreatedAt) {
        return toBuilder().mrCreatedAt(mrCreatedAt).build();
    }

    public TaskState withLastPolledAt(long lastPolledAt) {
        return toBuilder().lastPolledAt(lastPolledAt).build();
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
                .remoteUrl(remoteUrl).title(title).mrUrl(mrUrl).ticketUrl(ticketUrl)
                .mrCreatedAt(mrCreatedAt).lastPolledAt(lastPolledAt).autoReview(autoReview)
                .usage(usage).history(history);
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
        private long mrCreatedAt;
        private long lastPolledAt;
        private Boolean autoReview;
        private TokenUsage usage;
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
                    lastActiveTimestamp > 0 ? lastActiveTimestamp : System.currentTimeMillis()));
            return new TaskState(project, worktreePath, status, lastActiveTimestamp, message, alias,
                    remoteUrl, title, mrUrl, ticketUrl, mrCreatedAt, lastPolledAt, autoReview, usage, log);
        }
    }
}
