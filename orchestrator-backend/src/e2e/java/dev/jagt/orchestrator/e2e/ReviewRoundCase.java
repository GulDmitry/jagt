package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.flow.TaskStatus;

import java.util.List;

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
                        TaskStatus.REVIEWED, "checks success, nothing unresolved — waiting for an approval", false),
                new ReviewRoundCase("approved by a human",
                        facts(true, "success", List.of()),
                        TaskStatus.APPROVED, "approved, checks success", false),
                new ReviewRoundCase("checks still running",
                        facts(false, "running", List.of()),
                        TaskStatus.CI_POLLING, "nothing unresolved yet, not approved — waiting", false),
                new ReviewRoundCase("the request cannot be read", null,
                        TaskStatus.CI_POLLING, "error: read failed", false),
                new ReviewRoundCase("the host says there is no such request",
                        new ReviewFacts(false, false, "none", List.of()),
                        TaskStatus.CI_POLLING, "no such request", false));
    }

    private static ReviewFacts facts(boolean approved, String pipeline, List<String> comments) {
        return new ReviewFacts(true, approved, pipeline, comments);
    }

    @Override
    public String toString() {
        return name;
    }
}
