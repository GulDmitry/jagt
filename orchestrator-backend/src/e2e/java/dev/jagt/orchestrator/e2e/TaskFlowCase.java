package dev.jagt.orchestrator.e2e;

import java.util.List;

/**
 * One combination of the swappable pieces + config flags the task flow must behave identically under. The
 * matrix is data, so widening coverage is adding a row here — not writing another test.
 *
 * <p>What is NOT a row yet, and why (a silent gap reads as coverage): {@code claude} and {@code codex} as the
 * agent, because both would spawn a real CLI and spend real money — that is what {@code stub} exists for, and
 * the per-agent provisioning differences are unit-tested in each runtime's own test. The notifier, editor and
 * terminal drivers are replaced by headless doubles in the run (a GUI cannot be asserted and must not open on
 * a developer's machine), so they are not rows either until a Linux driver exists to compare against.
 *
 * @param viewMode   {@code viewer.viewMode} — shared vs one terminal tab per task
 * @param autoReview {@code autoReview.enabled} — whether a shipped task gets polled unattended
 */
record TaskFlowCase(String viewMode, boolean autoReview) {

    static List<TaskFlowCase> matrix() {
        return List.of(
                new TaskFlowCase("shared", false),
                new TaskFlowCase("shared", true),
                new TaskFlowCase("tab-per-task", false),
                new TaskFlowCase("tab-per-task", true));
    }

    @Override
    public String toString() {
        return "viewMode=" + viewMode + ", autoReview=" + autoReview;
    }
}
