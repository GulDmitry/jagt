package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders the SHARED {@link TaskView} projection — the same one the web board consumes — so a phase, an owner and
 * a next move cannot mean one thing here and another there.
 */
@Component
@RequiredArgsConstructor
public class DashboardRenderer {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MM HH:mm");

    // Defined ONCE: a surface that colours one column finds it by the offsets below.
    public static final int ALIAS_W = 5;
    public static final int TASK_W = 11;
    private static final int STATUS_W = 15;
    private static final int PROJECT_W = 8;
    private static final int ACTIVE_W = 11;
    private static final int TOKENS_W = 7;
    // Every column is TRUNCATED to its width, not merely padded to it: one value a character too long shifts
    // every column after it, and `MasterShell` slices the row at the fixed offsets below to colour it.
    private static final String ROW_FORMAT = "%-" + ALIAS_W + "." + ALIAS_W + "s %-" + TASK_W + "." + TASK_W
            + "s %-" + STATUS_W + "." + STATUS_W + "s %-" + PROJECT_W + "." + PROJECT_W + "s %-" + ACTIVE_W
            + "." + ACTIVE_W + "s %-" + TOKENS_W + "." + TOKENS_W + "s %s%n";
    public static final int COL_ALIAS = 0;
    public static final int COL_TASK = ALIAS_W + 1;
    public static final int COL_TITLE =
            ALIAS_W + 1 + TASK_W + 1 + STATUS_W + 1 + PROJECT_W + 1 + ACTIVE_W + 1 + TOKENS_W + 1;

    private final TaskViews taskViews;
    private final UsageTracker usageTracker;
    private final Jobs jobs;

    public String render() {
        long now = System.currentTimeMillis();
        TaskViews.Snapshot snapshot = taskViews.snapshot();
        List<TaskView> tasks = snapshot.tasks();
        StringBuilder out = new StringBuilder();
        out.append("jagt orchestrator — ").append(tasks.size()).append(" task(s)   updated ")
                .append(LocalTime.now().format(CLOCK)).append(sessionSpend()).append('\n');
        // Its own line: an 80-column header has no room left, and a wrapped header costs a dashboard row.
        // INDENTED, because a line starting with a space is what marks a task row for colouring.
        out.append("  ").append(snapshot.cadence().summary())
                .append(unattended(jobs.summary(now), now)).append('\n');
        out.append(String.format(ROW_FORMAT, "ALIAS", "TASK", "STATUS", "PROJECT", "ACTIVE", "TOKENS",
                "TITLE"));
        for (TaskView task : tasks) {
            out.append(String.format(ROW_FORMAT, task.alias() == null ? "-" : task.alias(), task.id(),
                    task.statusLabel(), task.project(), stamp(task.lastActiveAt()), tokens(task.tokens()),
                    oneLineTitle(task.title())));
            if (task.ticketUrl() != null && !task.ticketUrl().isBlank()) {
                out.append("                    └ ").append(task.ticketUrl()).append('\n');
            }
            if (task.reviewRequestUrl() != null) {
                out.append("                    └ ").append(checks(task.pipeline()))
                        .append(approval(task.approved())).append(task.reviewRequestUrl()).append('\n');
            }
            if (task.detail() != null && !task.detail().isBlank()) {
                out.append("                    └ ").append(task.detail()).append('\n');
            }
            // The one artifact of a review round nothing else announces: the agent's intended answers, sitting
            // in the worktree. A human who does not know the convention ships them unread.
            if (task.draftedReplies()) {
                out.append("                    └ drafted review replies — `replies ")
                        .append(task.alias() == null ? task.id() : task.alias())
                        .append("` reads them before you ship\n");
            }
            String watch = autoReviewLine(task.autoReview());
            if (!watch.isEmpty()) {
                out.append("                    └ ").append(watch).append('\n');
            }
            // Lead with WHOSE move it is: on a board of five tasks that is the fact a human scans for. The
            // duration is time in THIS status, not since the last activity — a keep-alive resets that stamp.
            out.append("                    → ").append(task.owner().label()).append(" · ")
                    .append(task.hint()).append("  (").append(task.statusLabel()).append(' ')
                    .append(DurationFormat.compact(now - task.statusSince()))
                    .append(requestOpen(task)).append(")\n");
        }
        if (tasks.isEmpty()) {
            out.append("(no tasks)\n");
        }
        return out.toString();
    }

    /**
     * The checks, only when they are not green: a red run while the task still reads out-for-review is what the
     * status alone cannot show.
     */
    private static String checks(Pipeline pipeline) {
        return switch (pipeline) {
            case RED -> "CHECKS RED · ";
            case RUNNING -> "checks running · ";
            case GREEN, NONE -> "";
        };
    }

    /**
     * The one thing a human waits for that no status shows until it has already happened. Silent until a read has
     * said either way: an unread request is not an unapproved one.
     */
    private static String approval(Boolean approved) {
        if (approved == null) {
            return "";
        }
        return approved ? "APPROVED · " : "not approved · ";
    }

    /**
     * Work nobody watches, said before it acts rather than only in the report that has to be asked for. A failed
     * run outranks the countdown: the next run is not news while the last one is still broken.
     */
    private static String unattended(Jobs.Summary summary, long now) {
        if (summary.failing() > 0) {
            return " · " + summary.failing() + " job(s) failing";
        }
        return summary.nextRunAt() == null ? ""
                : " · jobs next in " + DurationFormat.countdown(summary.nextRunAt() - now);
    }

    /**
     * How long the review request has been open, next to the status clock a respawned agent resets — the two
     * answer different questions, and this is the one a human means by "how long has this been waiting". Worded
     * as the board words it, and as short: this is already the longest line on the dashboard, and a wrap costs
     * every polled task a row.
     */
    private static String requestOpen(TaskView task) {
        return task.requestOpenedAt() <= 0
                ? ""
                : " · MR " + DurationFormat.compact(System.currentTimeMillis() - task.requestOpenedAt());
    }

    /**
     * What the poller is about to do with this task, or nothing when it is not its business. The words are the
     * projection's own ({@link AutoReviewWatch#note}); only the countdown is rendered here, from the absolute
     * stamp, so it is right however long ago the projection was built.
     */
    private static String autoReviewLine(AutoReviewWatch watch) {
        if (watch.state() == AutoReviewWatch.State.NONE) {
            return "";
        }
        // A terminal line has nothing else to say what this is about, unlike a chip that IS the poller.
        return watch.state() == AutoReviewWatch.State.WATCHING
                ? "auto-review · " + watch.note() + " " + due(watch.nextPollAt())
                : "auto-review · " + watch.note();
    }

    private static String due(long nextPollAt) {
        long remaining = nextPollAt - System.currentTimeMillis();
        return remaining <= 0 ? "due now" : "in " + DurationFormat.countdown(remaining);
    }

    static String stamp(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(STAMP);
    }

    private static String tokens(long tokens) {
        return tokens == 0 ? "-" : TokenFormat.compact(tokens);
    }

    /** Omitted until something has been spent, and kept short so the header still fits 80 columns. */
    private String sessionSpend() {
        TokenUsage session = usageTracker.session();
        return session.isNone() ? "" : "   spend " + session.calls()
                + (session.calls() == 1 ? " call / " : " calls / ")
                + TokenFormat.compact(session.total()) + " tok";
    }

    /** Never truncated: it is the last column, so a surface that has to clip it can. */
    private static String oneLineTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        return title.strip().replaceAll("\\s+", " ");
    }
}
