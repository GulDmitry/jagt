package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskChoice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The read-only views of jagt's state: the dashboard, the token-spend stats, the tasks to pick from.
 * Grouped into one collaborator so a caller that shows state (the Master shell, the HTTP endpoints) takes
 * a single dependency instead of one per screen — and so both surfaces are guaranteed to show the exact
 * same text.
 */
@Component
@RequiredArgsConstructor
public class StateViews {

    private final DashboardRenderer dashboard;
    private final UsageStatsRenderer usageStats;
    private final WorktreeOrphanScanner orphanScanner;
    private final TaskViews taskViews;

    public String dashboard() {
        return dashboard.render();
    }

    public String usageStats() {
        return usageStats.render();
    }

    public java.util.List<TaskChoice> taskChoices() {
        return taskViews.choices();
    }

    /** What is on disk that state.json knows nothing about — leftover worktrees and the secrets in them. */
    public String orphanedWorktrees() {
        return orphanScanner.report();
    }
}
