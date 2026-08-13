package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskView;
import dev.jagt.orchestrator.model.TokenUsage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders the plain-text task dashboard for the console and the {@code /status} endpoint. It renders the
 * SHARED {@link TaskView} projection — the same one the web board consumes — so a phase, an owner and a
 * next move cannot mean one thing here and another there.
 */
@Component
public class DashboardRenderer {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** Last-active stamp: day-month hour:minute, no year/seconds — the ACTIVE column the table sorts on. */
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MM HH:mm");

    // Column widths, defined ONCE and shared by the header + every task row. The Master TUI colors the
    // ALIAS / TASK / TITLE columns by the offsets below, so this is the single source of truth for the layout.
    public static final int ALIAS_W = 5;
    public static final int TASK_W = 11;
    private static final int STATUS_W = 14;
    private static final int PROJECT_W = 8;
    private static final int ACTIVE_W = 11;
    private static final int TOKENS_W = 7;
    private static final String ROW_FORMAT = "%-" + ALIAS_W + "s %-" + TASK_W + "s %-" + STATUS_W
            + "s %-" + PROJECT_W + "s %-" + ACTIVE_W + "s %-" + TOKENS_W + "s %s%n";
    /** Start column of the ALIAS / TASK / TITLE fields in a rendered row (for per-column coloring). */
    public static final int COL_ALIAS = 0;
    public static final int COL_TASK = ALIAS_W + 1;
    public static final int COL_TITLE =
            ALIAS_W + 1 + TASK_W + 1 + STATUS_W + 1 + PROJECT_W + 1 + ACTIVE_W + 1 + TOKENS_W + 1;

    private final TaskViews taskViews;
    private final UsageTracker usageTracker;
    private final ConfigService configService;

    public DashboardRenderer(TaskViews taskViews, UsageTracker usageTracker, ConfigService configService) {
        this.taskViews = taskViews;
        this.usageTracker = usageTracker;
        this.configService = configService;
    }

    public String render() {
        List<TaskView> tasks = taskViews.all();
        StringBuilder out = new StringBuilder();
        out.append("jagt orchestrator — ").append(occupancy(tasks.size())).append(" task(s)   updated ")
                .append(LocalTime.now().format(CLOCK)).append(sessionSpend()).append('\n').append('\n');
        out.append(String.format(ROW_FORMAT, "ALIAS", "TASK", "STATUS", "PROJECT", "ACTIVE ▼", "TOKENS",
                "TITLE"));
        for (TaskView task : tasks) {
            out.append(String.format(ROW_FORMAT, task.alias() == null ? "-" : task.alias(), task.id(),
                    task.status(), task.project(), stamp(task.lastActiveAt()), tokens(task.tokens()),
                    oneLineTitle(task.title())));
            if (task.ticketUrl() != null && !task.ticketUrl().isBlank()) {
                out.append("                    └ ").append(task.ticketUrl()).append('\n');
            }
            if (task.detail() != null && !task.detail().isBlank()) {
                out.append("                    └ ").append(task.detail()).append('\n');
            }
            // The one artifact of a review round nothing else announces: the agent's intended answers, sitting
            // in the worktree. A human who does not know the convention ships them unread.
            if (task.draftedReplies()) {
                out.append("                    └ drafted review replies in review_replies.md — `ide ")
                        .append(task.alias() == null ? task.id() : task.alias())
                        .append("` before you ship\n");
            }
            // Lead with WHOSE move it is: on a board of five tasks that is the fact a human scans for. The
            // duration is time in THIS status, not since the last activity — a keep-alive resets that stamp.
            out.append("                    → ").append(task.owner().label()).append(" · ")
                    .append(task.hint()).append("  (")
                    .append(compactDuration(System.currentTimeMillis() - task.statusSince()))
                    .append(" in ").append(task.status()).append(")\n");
        }
        if (tasks.isEmpty()) {
            out.append("(no tasks)\n");
        }
        return out.toString();
    }

    /**
     * "2/3" while a cap is configured, plain "2" when the human opted out — the cap has to be readable BEFORE
     * a `do` is refused for hitting it, which is the whole point of enforcing one.
     */
    private String occupancy(int tasks) {
        int capacity = configService.load().agent().maxConcurrentTasksOrDefault();
        return capacity > 0 ? tasks + "/" + capacity : String.valueOf(tasks);
    }

    /** Coarse on purpose: one glance should say "minutes" or "days", and a second column of digits would not. */
    static String compactDuration(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        if (minutes < 60) {
            return minutes + "m";
        }
        return minutes < 60 * 24 ? minutes / 60 + "h" : minutes / (60 * 24) + "d";
    }

    static String stamp(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(STAMP);
    }

    /** The task's own column: total tokens jagt spent on it, or a dash when it has cost nothing yet. */
    private static String tokens(long tokens) {
        return tokens == 0 ? "-" : TokenFormat.compact(tokens);
    }

    /** Session total in the header — omitted until something has been spent, and kept short enough that
     *  the header still fits an 80-column terminal (a wrapped header costs a dashboard row). */
    private String sessionSpend() {
        TokenUsage session = usageTracker.session();
        return session.isNone() ? "" : "   spend " + session.calls()
                + (session.calls() == 1 ? " call / " : " calls / ")
                + TokenFormat.compact(session.total()) + " tok";
    }

    /** The ticket title on one line (whitespace collapsed), shown in FULL — it's the last column, so the
     *  Master TUI clips it to the window width and {@code /status} shows all of it. */
    private static String oneLineTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        return title.strip().replaceAll("\\s+", " ");
    }
}
