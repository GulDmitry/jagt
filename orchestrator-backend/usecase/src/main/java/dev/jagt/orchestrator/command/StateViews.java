package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.DashboardRenderer;
import dev.jagt.orchestrator.task.TaskChoice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The read-only views of jagt's state, grouped into ONE collaborator: a caller that shows state takes a single
 * dependency instead of one per screen, and every surface is then showing the same text.
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
