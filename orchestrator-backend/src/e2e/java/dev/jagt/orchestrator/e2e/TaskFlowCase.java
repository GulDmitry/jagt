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
 * <p>Also not a row, deliberately: what happens BETWEEN creation and teardown — ship, a review round, deploy,
 * revert, resume. Those need a host and an agent that answers, they behave the same however terminals are
 * arranged, and running them four times over would only pay for that twice. {@link ReviewAndDeployFlowTest}
 * covers them on one combination, over its own matrix of what a round can report.
 *
 * @param viewMode      {@code viewer.viewMode} — shared vs one terminal tab per task
 * @param autoReview    {@code autoReview.enabled} — whether a shipped task gets polled unattended
 * @param agentSession  the tmux session the agent's window must end up in, spelled out rather than derived:
 *                      a row that computes it the way the code does would agree with any renaming of it
 */
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
