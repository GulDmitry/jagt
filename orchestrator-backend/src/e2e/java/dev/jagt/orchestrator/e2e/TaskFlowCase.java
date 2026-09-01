package dev.jagt.orchestrator.e2e;

import java.util.List;

record TaskFlowCase(String viewMode, boolean autoReview, String agentSession) {

    static List<TaskFlowCase> matrix() {
        return List.of(
                new TaskFlowCase("shared", false, "jagt-e2e"),
                new TaskFlowCase("shared", true, "jagt-e2e"),
                new TaskFlowCase("tab-per-task", false, "jagt-e2e-ABC-1"),
                new TaskFlowCase("tab-per-task", true, "jagt-e2e-ABC-1"));
    }

    @Override
    public String toString() {
        return "viewMode=" + viewMode + ", autoReview=" + autoReview;
    }
}
