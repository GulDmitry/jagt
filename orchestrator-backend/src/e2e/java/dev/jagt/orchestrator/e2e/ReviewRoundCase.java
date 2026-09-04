package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.flow.TaskStatus;

import java.util.List;

record ReviewRoundCase(String name, ReviewFacts round, TaskStatus expected, String sentence, boolean briefed) {

    static List<ReviewRoundCase> matrix() {
        return List.of(
                new ReviewRoundCase("threads awaiting an answer",
                        facts(false, "success", List.of("http://mr/1#note_1\nbot: tighten this\ndev: it is bound",
                                "http://mr/1#note_4\nhuman: rename it")),
                        TaskStatus.CI_POLLING, "2 thread(s) relayed, checks success", true),
                new ReviewRoundCase("red checks, nothing unresolved",
                        facts(false, "failed", List.of()),
                        TaskStatus.CI_POLLING, "0 thread(s) relayed, checks failed", true),
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

    private static ReviewFacts facts(boolean approved, String pipeline, List<String> threads) {
        return new ReviewFacts(true, approved, pipeline, threads);
    }

    @Override
    public String toString() {
        return name;
    }
}
