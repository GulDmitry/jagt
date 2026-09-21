package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.task.MergeRequestFacts;

import java.util.ArrayList;
import java.util.List;

/** What an answer about an open review request has to hold for a `resume` to act on it. */
public final class MergeRequestRead {

    public static final Schema SCHEMA = Schema.answer()
            .required("exists", "boolean", null)
            .required("failure", "string", null)
            .required("sourceBranch", "string", null)
            .required("targetBranch", "string", null)
            .required("title", "string", null);

    private MergeRequestRead() {
    }

    /** A request that exists names both of its branches: a resume with one missing has no task to cut. */
    public static List<Violation> violations(MergeRequestFacts facts) {
        if (!facts.exists()) {
            return List.of();
        }
        List<Violation> found = new ArrayList<>();
        if (blank(facts.sourceBranch())) {
            found.add(new Violation("sourceBranch", "the branch the request is FROM; it becomes the task"));
        }
        if (blank(facts.targetBranch())) {
            found.add(new Violation("targetBranch", "the branch the request is INTO; the next ship targets it"));
        }
        return List.copyOf(found);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
