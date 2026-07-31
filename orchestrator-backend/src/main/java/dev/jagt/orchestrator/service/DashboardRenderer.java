package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.DashboardLine;
import dev.jagt.orchestrator.model.NextMove;
import dev.jagt.orchestrator.model.TaskState;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

    // Column widths, defined ONCE and shared by the header + every task row. The Master TUI colors the
    // ALIAS / TASK / TITLE columns by the offsets below, so this is the single source of truth for the layout.
    public static final int ALIAS_W = 5;
    public static final int TASK_W = 11;
    private static final int STATUS_W = 14;
    private static final int PROJECT_W = 8;
    private static final int ACTIVE_W = 9;
    private static final String ROW_FORMAT = "%-" + ALIAS_W + "s %-" + TASK_W + "s %-" + STATUS_W
            + "s %-" + PROJECT_W + "s %-" + ACTIVE_W + "s %s%n";
    /** Start column of the ALIAS / TASK / TITLE fields in a rendered row (for per-column coloring). */
    public static final int COL_ALIAS = 0;
    public static final int COL_TASK = ALIAS_W + 1;
    public static final int COL_TITLE = ALIAS_W + 1 + TASK_W + 1 + STATUS_W + 1 + PROJECT_W + 1 + ACTIVE_W + 1;

    private final StateService stateService;

    public DashboardRenderer(StateService stateService) {
        this.stateService = stateService;
    }

    public String render() {
        Map<String, TaskState> tasks = stateService.tasks();
        StringBuilder out = new StringBuilder();
        out.append("jagt orchestrator — ").append(tasks.size()).append(" task(s)   updated ")
                .append(LocalTime.now().format(CLOCK)).append('\n').append('\n');
        out.append(String.format(ROW_FORMAT, "ALIAS", "TASK", "STATUS", "PROJECT", "ACTIVE", "TITLE"));
        long now = System.currentTimeMillis();
        tasks.forEach((id, t) -> {
            long minutes = (now - t.lastActiveTimestamp()) / 60_000;
            String active = minutes < 1 ? "just now" : minutes + "m ago";
            out.append(String.format(ROW_FORMAT,
                    t.alias() == null ? "-" : t.alias(), id, t.status(), t.project(), active, oneLineTitle(t.title())));
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

    /** The ticket title on one line (whitespace collapsed), shown in FULL — it's the last column, so the
     *  Master TUI clips it to the window width and {@code /status} shows all of it. */
    private static String oneLineTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        return title.strip().replaceAll("\\s+", " ");
    }
}
