package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskChoice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The read-only views of jagt's state: the dashboard, the stats report, what jagt did unattended, and the
 * tasks to pick from.
 * Grouped into one collaborator so a caller that shows state (the Master shell, the HTTP endpoints) takes
 * a single dependency instead of one per screen — and so both surfaces are guaranteed to show the exact
 * same text.
 */
@Component
@RequiredArgsConstructor
public class StateViews {

    private final DashboardRenderer dashboard;
    private final StatsReport stats;
    private final TaskViews taskViews;
    private final ActivityReport activity;

    public String dashboard() {
        return dashboard.render();
    }

    public String stats() {
        return stats.render();
    }

    public String activity() {
        return activity.render();
    }

    public java.util.List<TaskChoice> taskChoices() {
        return taskViews.choices();
    }
}
