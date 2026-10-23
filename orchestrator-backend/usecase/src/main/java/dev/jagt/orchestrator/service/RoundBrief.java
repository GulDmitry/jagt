package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.task.ReviewFacts;

final class RoundBrief {

    private RoundBrief() {
    }

    /** The round's facts among the brief's round sections, those its checks and threads call for. */
    static String of(String mrUrl, ReviewFacts r, String said, SessionBrief sessionBrief) {
        StringBuilder brief = new StringBuilder("Review round for ").append(mrUrl).append(".\n");
        if (Pipeline.of(said) == Pipeline.RED) {
            brief.append("Checks: ").append(said).append(" — find out why and fix it.\n");
            if (!r.pipelineFailure().isBlank()) {
                brief.append("<checks>\n").append(r.pipelineFailure()).append("\n</checks>\n");
            }
            brief.append(section(sessionBrief, "round-checks"));
        }
        if (!r.threads().isEmpty()) {
            brief.append(section(sessionBrief, "round-threads")).append("<threads>\n");
            r.threads().forEach(thread -> brief.append(thread).append("\n\n"));
            brief.append("</threads>\n");
        }
        // An unanswered question ENDS the round rather than parking in it: staying CI_POLLING would have the poll
        // re-brief the agent on the very comments it was told to hold.
        brief.append(section(sessionBrief, r.threads().isEmpty() ? "round-checks-done" : "round-threads-done"));
        return brief.toString().stripTrailing();
    }

    private static String section(SessionBrief sessionBrief, String step) {
        return sessionBrief.section(step).filter(text -> !text.isBlank()).map(text -> text + "\n").orElse("");
    }
}
