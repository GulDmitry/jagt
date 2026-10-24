package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.task.ReviewFacts;

import java.util.List;

/** What an answer about a review round has to hold before a round is relayed off it. */
public final class ReviewRead {

    public static final Schema SCHEMA = Schema.answer()
            .required("exists", "boolean", null)
            .required("failure", "string", null)
            .required("approved", "boolean", null)
            .choiceRequired("pipelineStatus", List.of("success", "failed", "running", "none", "unknown"), null)
            .required("pipelineFailure", "string", null)
            .required("openedAt", "string", null)
            .requiredTexts("threads", null);

    private ReviewRead() {
    }

    /** What went wrong belongs to a failing run or a listing not got: under any other verdict it describes nothing. */
    public static List<Violation> violations(ReviewFacts facts) {
        String said = facts.pipelineStatus() == null ? "" : facts.pipelineStatus().strip().toLowerCase(java.util.Locale.ROOT);
        boolean wrong = said.contains("fail") || said.equals("unknown");
        return !facts.pipelineFailure().isBlank() && !wrong
                ? List.of(new Violation("pipelineFailure",
                        "given only where pipelineStatus is failed or unknown; it said " + facts.pipelineStatus()))
                : List.of();
    }
}
