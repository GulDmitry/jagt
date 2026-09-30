package dev.jagt.orchestrator.task;

import java.util.Arrays;
import java.util.List;

/**
 * A document one step of a task's life leaves behind for the next step to read, in the worktree jagt cut. A
 * capability declares which of them it touches rather than being handed the content: the file on disk is what the
 * agent is editing, so a copy passed through a signature would drift from it.
 *
 * <p>The briefing is not here — which file holds it is {@code AgentRuntime}'s answer, not the task's.
 */
public enum Artifact {

    /** The one instruction standing right now, and no older one. */
    CONTEXT("task_context.md"),
    /** What the agent means to do, before it writes anything. */
    PLAN("plan.md"),
    /** What the agent means to answer, before anything is posted. */
    REPLIES("review_replies.md"),
    /** What the unattended reviewer concluded about the round, and the verdict jagt reads back. */
    REVIEW("master-review.md"),
    /** What earlier rounds of this task decided, which a later reader does not reopen without a blocking reason. */
    DECISIONS("master-decisions.md");

    private final String fileName;

    Artifact(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }

    /** Every name jagt writes into a worktree of its own accord, so no caller keeps a second list. */
    public static List<String> fileNames() {
        return Arrays.stream(values()).map(Artifact::fileName).toList();
    }
}
