package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.task.Outside;
import dev.jagt.orchestrator.task.ReviewFacts;

import java.util.ArrayList;
import java.util.List;

final class RoundBrief {

    private static final String CHECKS = "round-checks";
    private static final String THREADS = "round-threads";

    private RoundBrief() {
    }

    /** The brief's round sections this round's checks and threads call for, in the order they are told. */
    static List<String> steps(ReviewFacts r, String said) {
        List<String> steps = new ArrayList<>();
        if (Pipeline.of(said) == Pipeline.RED) {
            steps.add(CHECKS);
        }
        if (!r.threads().isEmpty()) {
            steps.add(THREADS);
        }
        // An unanswered question ENDS the round rather than parking in it: staying CI_POLLING would have the poll
        // re-brief the agent on the very comments it was told to hold.
        steps.add(r.threads().isEmpty() ? "round-checks-done" : "round-threads-done");
        return List.copyOf(steps);
    }

    /** The round's facts among the brief's round sections. */
    static String of(String mrUrl, ReviewFacts r, String said, SessionBrief sessionBrief) {
        List<String> steps = steps(r, said);
        StringBuilder brief = new StringBuilder("Review round for ").append(mrUrl).append(".\n");
        if (steps.contains(CHECKS)) {
            brief.append("Checks: ").append(said).append(" — find out why and fix it.\n");
            if (!r.pipelineFailure().isBlank()) {
                brief.append(Outside.fence("checks", r.pipelineFailure())).append('\n');
            }
            brief.append(section(sessionBrief, CHECKS));
        }
        if (steps.contains(THREADS)) {
            brief.append(section(sessionBrief, THREADS))
                    .append(Outside.fence("threads", String.join("\n\n", r.threads()))).append('\n');
        }
        brief.append(section(sessionBrief, steps.getLast()));
        return brief.toString().stripTrailing();
    }

    private static String section(SessionBrief sessionBrief, String step) {
        return sessionBrief.section(step).filter(text -> !text.isBlank()).map(text -> text + "\n").orElse("");
    }
}
