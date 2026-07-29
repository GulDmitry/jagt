package dev.jawo.orchestrator.service;

import dev.jawo.orchestrator.model.DashboardLine;
import dev.jawo.orchestrator.model.NextMove;
import dev.jawo.orchestrator.model.TaskState;
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

    private final StateService stateService;

    public DashboardRenderer(StateService stateService) {
        this.stateService = stateService;
    }

    public String render() {
        Map<String, TaskState> tasks = stateService.tasks();
        StringBuilder out = new StringBuilder();
        out.append("jawo orchestrator — ").append(tasks.size()).append(" task(s)   updated ")
                .append(LocalTime.now().format(CLOCK)).append('\n').append('\n');
        out.append(String.format("%-5s %-11s %-14s %-8s %-9s %s%n",
                "ALIAS", "TASK", "STATUS", "PROJECT", "ACTIVE", "TITLE"));
        long now = System.currentTimeMillis();
        tasks.forEach((id, t) -> {
            long minutes = (now - t.lastActiveTimestamp()) / 60_000;
            String active = minutes < 1 ? "just now" : minutes + "m ago";
            out.append(String.format("%-5s %-11s %-14s %-8s %-9s %s%n",
                    t.alias() == null ? "-" : t.alias(), id, t.status(), t.project(), active, shortTitle(t.title())));
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

    /** One-line, truncated ticket title so every row says what the task is, even when many. */
    private static String shortTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String oneLine = title.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= 34 ? oneLine : oneLine.substring(0, 33) + "…";
    }
}
