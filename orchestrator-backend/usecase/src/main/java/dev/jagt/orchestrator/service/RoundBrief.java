package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.task.ReviewFacts;

final class RoundBrief {

    private RoundBrief() {
    }

    /**
     * The round is relayed as a JUDGEMENT, not as a work order: an agent handed a list of comments complies with
     * all of them, wrong ones included. The brief opens on the three routes a thread can take.
     */
    static String of(String mrUrl, ReviewFacts r, String said) {
        StringBuilder brief = new StringBuilder("Review round for ").append(mrUrl).append(".\n");
        if (Pipeline.of(said) == Pipeline.RED) {
            brief.append("Checks: ").append(said).append(" — find out why and fix it.\n");
            if (!r.pipelineFailure().isBlank()) {
                brief.append("<checks>\n").append(r.pipelineFailure()).append("\n</checks>\n");
            }
            brief.append("""
                    <how_to_fix_checks>
                    <checks> is a clue read cheaply, not the diagnosis. Find the job that failed yourself: the
                    request's newest pipeline, down into any pipeline it triggered. Read that job's whole log and its
                    reports, and a verdict it only links to, such as a quality gate. Climb the tools this machine
                    carries, cheapest first: an MCP, then a CLI (the code host's, the cluster's, the database's), then a
                    browser as the last try.
                    Reproduce it with the command that job ran, fix it, and run that command again until it passes.
                    A gate fails on EVERY condition it lists: answer each. One no local command computes, such as
                    duplication, is checked against that tool's own findings (the duplicated blocks, the uncovered
                    lines) until none of them is in what you changed.
                    A red check is this task's to turn green, code that predates it included: an override, an
                    exception or a human's action is no answer while a change here can pass it.
                    A failure that is not in the code or does not reproduce — a runner, the network, a timeout, a
                    flaky test — or one no tool here can read: change nothing, set status REVIEW_PENDING with
                    outcome=question naming the job and why.
                    </how_to_fix_checks>
                    """);
        }
        if (!r.threads().isEmpty()) {
            brief.append("""
                    <how_to_judge>
                    Your job this round is to get the code RIGHT, not to satisfy the reviewer. A comment is an
                    argument from someone who read the diff, not the system: it can be mistaken about the
                    architecture, and you have the code in front of you. Each block below is one THREAD, its
                    notes oldest first: what you answer is its NEWEST note. Where a reply of your own is
                    followed by the reviewer answering back, that answer is the argument to weigh now — never
                    re-post the reply it has already read. Where the newest note is your OWN and nobody
                    answered it, that thread is waiting on the reviewer: leave it alone and give it no block.
                    Weigh every other thread, then take exactly ONE route per thread:
                    - Right: fix it LOCALLY (no commit, no push).
                    - Wrong: change NOTHING and reply with the one concrete technical reason it is wrong.
                    - Right, but beyond the ticket: change NOTHING and reply that it is a task of its own, named
                      in a few words. Step past the ticket only as far as a fix it asks for needs.
                    - You cannot tell, or it is right but forces a design decision nobody gave you: do not guess
                      and do not half-implement it. Leave that comment's code alone, put the question in its
                      review_replies.md block, and hand the round back: set REVIEW_PENDING
                      with outcome=question and the question in the message (few words).
                    Implementing a change you believe is wrong is the worst outcome available to you: silent
                    compliance is invisible in a diff. Never report a fix you did not make.
                    </how_to_judge>
                    <replies>
                    review_replies.md is what the human READS to approve this round — end to end, in one pass,
                    before anything is posted. Write ONE block per thread, in this shape and nothing else:

                    ## <thread link, or file:line>
                    > <the newest note, trimmed to the sentence that matters>
                    FIXED | NO CHANGE | QUESTION - <the reply, one or two sentences>

                    The verdict word is for the human; what follows the dash is posted verbatim. Every thread
                    you answer gets a block, the ones you push back on and ask about included.

                    NECESSARY AND SUFFICIENT is the test for every line: drop it if the answer survives without
                    it, and answer completely with what is left. No restating the comment beyond the quoted
                    line, no thanks, no re-describing the diff, no test or build status, no headers or bullets
                    inside a reply. If the file is longer than the diff it explains, it is wrong.

                    The file holds DRAFTS: post nothing and resolve no thread this round. Nothing leaves this
                    machine until the human ships.
                    </replies>
                    <threads>
                    """);
            r.threads().forEach(thread -> brief.append(thread).append("\n\n"));
            brief.append("</threads>\n");
        }
        // An unanswered question ENDS the round rather than parking in it: staying CI_POLLING would have the poll
        // re-brief the agent on the very comments it was told to hold. The round's OUTCOME is a field of its own,
        // because all three end at the same status.
        brief.append(r.threads().isEmpty()
                ? "When the build is fixed locally, set status REVIEW_PENDING (outcome=progress)."
                : """
                        When every thread is fixed, answered or asked about, set status REVIEW_PENDING with the
                        outcome of THIS round:
                        - outcome=question — a question of yours is still open; it rides in the message.
                        - outcome=no_changes — you changed no code (all already handled, or you pushed back on
                          every comment). jagt reads the worktree, so claiming this over an edited file records a
                          round with a diff instead.
                        - outcome=progress — you fixed code locally and there is a diff to read.""");
        brief.append("\nDo NOT push or post anything yourself.");
        return brief.toString();
    }
}
