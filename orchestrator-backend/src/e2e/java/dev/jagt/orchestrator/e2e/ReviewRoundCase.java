package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.flow.TaskStatus;

import java.util.List;

/**
 * One round a sweep can meet and where it must leave the task. Data, so widening coverage is adding a row.
 *
 * @param round    what the host reports; null = the request cannot be read at all, which must leave the task
 *                 exactly where it was — an unreadable review that advanced one would read as a clean one
 * @param expected the status the task must hold afterwards
 * @param sentence what the human must be told, verbatim enough to catch a reword
 * @param briefed  whether the agent was handed this round to work on
 */
record ReviewRoundCase(String name, ReviewFacts round, TaskStatus expected, String sentence, boolean briefed) {

    static List<ReviewRoundCase> matrix() {
        return List.of(
                new ReviewRoundCase("unresolved comments",
                        facts(false, "success", List.of("bot (Widget.java:12): tighten this", "human: rename it")),
                        TaskStatus.CI_POLLING, "2 comment(s) relayed, checks success", true),
                new ReviewRoundCase("red checks, nothing unresolved",
                        facts(false, "failed", List.of()),
                        TaskStatus.CI_POLLING, "0 comment(s) relayed, checks failed", true),
                new ReviewRoundCase("green and nothing unresolved",
                        facts(false, "success", List.of()),
                        TaskStatus.REVIEWED, "checks success, nothing unresolved — `deploy` or `done`", false),
                new ReviewRoundCase("approved by a human",
                        facts(true, "success", List.of()),
                        TaskStatus.APPROVED, "approved, checks success", false),
                new ReviewRoundCase("checks still running",
                        facts(false, "running", List.of()),
                        TaskStatus.CI_POLLING, "nothing unresolved yet, not approved — waiting", false),
                new ReviewRoundCase("the request cannot be read", null,
                        TaskStatus.CI_POLLING, "error: could not read", false));
    }

    private static ReviewFacts facts(boolean approved, String pipeline, List<String> comments) {
        return new ReviewFacts(true, approved, pipeline, comments);
    }

    @Override
    public String toString() {
        return name;
    }
}
