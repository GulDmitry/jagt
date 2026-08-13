package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.DashboardLine;
import dev.jagt.orchestrator.model.NextMove;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TokenUsage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;

/**
 * Renders the plain-text task dashboard. Shared by the {@code /status} HTTP endpoint
 * and the Master shell so both show the exact same, backend-computed view (the
 * {@code └} detail and {@code →} next-move come from {@link DashboardLine}/{@link NextMove},
 * never improvised).
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

    private final StateService stateService;
    private final UsageTracker usageTracker;

    public DashboardRenderer(StateService stateService, UsageTracker usageTracker) {
        this.stateService = stateService;
        this.usageTracker = usageTracker;
    }

    public String render() {
        Map<String, TaskState> tasks = stateService.tasks();
        StringBuilder out = new StringBuilder();
        out.append("jagt orchestrator — ").append(tasks.size()).append(" task(s)   updated ")
                .append(LocalTime.now().format(CLOCK)).append(sessionSpend()).append('\n').append('\n');
        out.append(String.format(ROW_FORMAT, "ALIAS", "TASK", "STATUS", "PROJECT", "ACTIVE ▼", "TOKENS",
                "TITLE"));
        tasks.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, TaskState> e) ->
                        e.getValue().lastActiveTimestamp()).reversed())
                .forEach(e -> {
            String id = e.getKey();
            TaskState t = e.getValue();
            out.append(String.format(ROW_FORMAT, t.alias() == null ? "-" : t.alias(), id, t.status(),
                    t.project(), stamp(t.lastActiveTimestamp()), tokens(t.usageOrNone()),
                    oneLineTitle(t.title())));
            if (t.ticketUrl() != null && !t.ticketUrl().isBlank()) {
                out.append("                    └ ").append(t.ticketUrl()).append('\n');
            }
            String detail = DashboardLine.forTask(id, t);
            if (!detail.isBlank()) {
                out.append("                    └ ").append(detail).append('\n');
            }
            out.append("                    → ").append(NextMove.forStatus(t.status())).append('\n');
        });
        if (tasks.isEmpty()) {
            out.append("(no tasks)\n");
        }
        return out.toString();
    }

    static String stamp(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(STAMP);
    }

    /** The task's own column: total tokens jagt spent on it, or a dash when it has cost nothing yet. */
    private static String tokens(TokenUsage usage) {
        return usage.isNone() ? "-" : TokenFormat.compact(usage.total());
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
