package dev.jagt.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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
        Boolean autoReview
) {

    public TaskState withStatus(TaskStatus status, String message) {
        return toBuilder().status(status).lastActiveTimestamp(System.currentTimeMillis())
                .message(message).build();
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

    /** A required-fields entry point; optional fields default to unset and are layered on with setters. */
    public static Builder builder(String project, String worktreePath, TaskStatus status) {
        return new Builder(project, worktreePath, status);
    }

    private Builder toBuilder() {
        return new Builder(project, worktreePath, status)
                .lastActiveTimestamp(lastActiveTimestamp).message(message).alias(alias)
                .remoteUrl(remoteUrl).title(title).mrUrl(mrUrl).ticketUrl(ticketUrl)
                .mrCreatedAt(mrCreatedAt).lastPolledAt(lastPolledAt).autoReview(autoReview);
    }

    /**
     * Mutable builder so callers set only the fields they care about — the record has thirteen fields and
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

        public TaskState build() {
            return new TaskState(project, worktreePath, status, lastActiveTimestamp, message, alias,
                    remoteUrl, title, mrUrl, ticketUrl, mrCreatedAt, lastPolledAt, autoReview);
        }
    }
}
