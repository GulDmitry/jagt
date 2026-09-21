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

    /** The failing job's lines belong to a failing run: quoted under any other verdict they describe nothing. */
    public static List<Violation> violations(ReviewFacts facts) {
        boolean red = facts.pipelineStatus() != null
                && facts.pipelineStatus().strip().toLowerCase(java.util.Locale.ROOT).contains("fail");
        return !facts.pipelineFailure().isBlank() && !red
                ? List.of(new Violation("pipelineFailure",
                        "given only where pipelineStatus is failed; it said " + facts.pipelineStatus()))
                : List.of();
    }
}
