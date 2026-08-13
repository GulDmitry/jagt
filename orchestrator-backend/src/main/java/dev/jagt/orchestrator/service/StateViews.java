package dev.jagt.orchestrator.service;

import org.springframework.stereotype.Component;

/**
 * The rendered, read-only text views of jagt's state: the task dashboard and the token-spend stats.
 * Grouped into one collaborator so a caller that shows state (the Master shell, the HTTP endpoints) takes
 * a single dependency instead of one per screen — and so both surfaces are guaranteed to show the exact
 * same text.
 */
@Component
public class StateViews {

    private final DashboardRenderer dashboard;
    private final UsageStatsRenderer usageStats;
    private final WorktreeOrphanScanner orphanScanner;

    public StateViews(DashboardRenderer dashboard, UsageStatsRenderer usageStats,
                      WorktreeOrphanScanner orphanScanner) {
        this.dashboard = dashboard;
        this.usageStats = usageStats;
        this.orphanScanner = orphanScanner;
    }

    public String dashboard() {
        return dashboard.render();
    }

    public String usageStats() {
        return usageStats.render();
    }

    /** What is on disk that state.json knows nothing about — leftover worktrees and the secrets in them. */
    public String orphanedWorktrees() {
        return orphanScanner.report();
    }
}
