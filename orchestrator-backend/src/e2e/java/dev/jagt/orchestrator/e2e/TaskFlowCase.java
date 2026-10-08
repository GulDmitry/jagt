package dev.jagt.orchestrator.e2e;

import java.util.List;

record TaskFlowCase(String viewMode, boolean autoReview, String agentSession) {

    static List<TaskFlowCase> matrix() {
        return List.of(
                new TaskFlowCase("shared", false, E2eWorkspace.TMUX_SESSION),
                new TaskFlowCase("shared", true, E2eWorkspace.TMUX_SESSION),
                new TaskFlowCase("tab-per-task", false, E2eWorkspace.TMUX_SESSION + "-ABC-1"),
                new TaskFlowCase("tab-per-task", true, E2eWorkspace.TMUX_SESSION + "-ABC-1"));
    }

    @Override
    public String toString() {
        return "viewMode=" + viewMode + ", autoReview=" + autoReview;
    }
}
