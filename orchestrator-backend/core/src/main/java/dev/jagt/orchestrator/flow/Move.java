package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AutoReviewWatch;

import java.util.List;

/**
 * What a task IS to a human: whose turn it is, which phase it reads as, what to do next and in what words.
 * Total over {@link TaskStatus} × {@link AgentReport}, the report being an input because all three outcomes of a
 * review round end at the same status. WHICH actions are legal is {@link FlowRules}. Liveness is projected as "not
 * live" for the rules: probing per task per render would cost a process spawn.
 */
public record Move(Phase phase, Owner owner, Attention attention, String ask, List<TaskAction> actions,
                   TaskAction primary, String hint) {

    public static Move forTask(TaskStatus status, boolean hasReviewRequest, RoundState round,
                               boolean agentSilent) {
        return forTask(status, hasReviewRequest, round, agentSilent, AutoReviewWatch.none());
    }

    /** The WATCH rather than a flag off it: a promised poll that STOPPED and no poller at all are different cards. */
    public static Move forTask(TaskStatus status, boolean hasReviewRequest, RoundState round,
                               boolean agentSilent, AutoReviewWatch watch) {
        boolean polled = watch.state() == AutoReviewWatch.State.WATCHING;
        Owner owner = ownerOf(status, hasReviewRequest, round, agentSilent, watch.stopped());
        Attention attention = attentionOf(owner, status, round, polled);
        return new Move(phaseOf(status), owner, attention,
                ask(status, hasReviewRequest, round, agentSilent, attention),
                FlowRules.allowed(status, Facts.projected(hasReviewRequest)),
                primaryOf(status, hasReviewRequest, round, polled),
                hint(status, hasReviewRequest, round, agentSilent, polled));
    }

    private static Phase phaseOf(TaskStatus status) {
        return switch (status) {
            case NEW, IN_PROGRESS -> Phase.BUILD;
            case REVIEW_PENDING -> Phase.REVIEW;
            case SHIPPING, CI_POLLING, CI_FAILED -> Phase.CHECK;
            case REVIEWED, APPROVED -> Phase.READY;
            case DEPLOYED, DEPLOY_CONFLICT, REVERTED -> Phase.DEPLOY;
            case DONE -> Phase.DONE;
        };
    }

    /** An agent that stopped is not working, whatever status it kept: the wait is the human's. */
    private static Owner ownerOf(TaskStatus status, boolean hasReviewRequest, RoundState round,
                                 boolean agentSilent, boolean pollStopped) {
        // ASKING IS STOPPING, whatever status it was asked from: the session waits until a human answers.
        if (asking(status, round)) {
            return Owner.YOU;
        }
        Owner owner = waitingOn(status, hasReviewRequest, round);
        if (owner == Owner.AGENT && agentSilent) {
            return Owner.YOU;
        }
        // A wait on the code host that nothing will look at again is a wait on the HUMAN.
        return owner == Owner.CI && pollStopped ? Owner.YOU : owner;
    }

    private static Attention attentionOf(Owner owner, TaskStatus status, RoundState round, boolean polled) {
        if (owner != Owner.YOU) {
            return Attention.NONE;
        }
        if (asking(status, round)) {
            return polled && status == TaskStatus.REVIEW_PENDING ? Attention.OPTIONAL : Attention.REQUIRED;
        }
        return status == TaskStatus.APPROVED || status == TaskStatus.REVERTED
                ? Attention.OPTIONAL : Attention.REQUIRED;
    }

    /**
     * WHICH act is wanted, in the words a chip has room for; null exactly when {@link Attention} is {@code NONE}.
     * The quiet tier says so in grammar rather than in colour: an imperative interrupts, an offer does not.
     */
    private static String ask(TaskStatus status, boolean hasReviewRequest, RoundState round, boolean agentSilent,
                              Attention attention) {
        if (attention == Attention.NONE) {
            return null;
        }
        String act = act(status, hasReviewRequest, round, agentSilent);
        // A status that reached the tier with no act of its own must not wear the word "null" on a card.
        if (act == null) {
            return null;
        }
        return attention == Attention.OPTIONAL ? "you can " + act : act;
    }

    private static String act(TaskStatus status, boolean hasReviewRequest, RoundState round,
                              boolean agentSilent) {
        if (asking(status, round)) {
            // A round already back is READ, not answered: the session that asked it has finished the round.
            return status == TaskStatus.REVIEW_PENDING ? "review the round" : "answer the session";
        }
        if (ownerOf(status) == Owner.AGENT && agentSilent) {
            return "check the stopped session";
        }
        return switch (status) {
            case REVIEW_PENDING -> switch (round.report()) {
                case NO_CHANGES -> !hasReviewRequest ? "open the review request"
                        : round.draftedReplies() ? "post the drafted replies" : "read the review";
                case QUESTION, PLAIN -> "review and ship";
            };
            case CI_POLLING -> hasReviewRequest ? "read the review" : "focus the agent";
            case CI_FAILED -> hasReviewRequest ? "relay the failed checks" : "focus the agent";
            case REVERTED -> hasReviewRequest ? "ship a fix" : "focus the agent";
            case REVIEWED -> hasReviewRequest ? "read the review" : "close the task";
            case APPROVED -> hasReviewRequest ? "deploy it" : "close the task";
            case DEPLOY_CONFLICT -> "resolve the conflict";
            // Unreachable: both ways an agent gives these up are answered above.
            case NEW, IN_PROGRESS, SHIPPING, DEPLOYED, DONE -> null;
        };
    }

    /** Whose turn it is before liveness and the poller are taken into account. */
    private static Owner waitingOn(TaskStatus status, boolean hasReviewRequest, RoundState round) {
        // The only move left hands the same threads back, so the wait belongs to whoever writes the next comment.
        if (status == TaskStatus.REVIEW_PENDING && hasReviewRequest
                && round.report() == AgentReport.NO_CHANGES && !round.draftedReplies()) {
            return Owner.CI;
        }
        // Out with the reviewers, with nothing for them to review: nobody but a human can move that.
        if (status.outForReview() && !hasReviewRequest) {
            return Owner.YOU;
        }
        return ownerOf(status);
    }

    /** A closed task's leftover message is not a question anybody still owes an answer to. */
    private static boolean asking(TaskStatus status, RoundState round) {
        return round.report() == AgentReport.QUESTION && status != TaskStatus.DONE;
    }

    /** Whose turn a status ALONE means. */
    public static Owner ownerOf(TaskStatus status) {
        return switch (status) {
            case NEW, IN_PROGRESS, SHIPPING -> Owner.AGENT;
            // REVIEWED is "nothing unresolved, checks green, NOT approved": handed in, waiting for a reviewer.
            case CI_POLLING, REVIEWED -> Owner.CI;
            case REVIEW_PENDING, CI_FAILED, APPROVED, DEPLOY_CONFLICT, REVERTED -> Owner.YOU;
            case DEPLOYED -> Owner.NOBODY;
            case DONE -> Owner.NOBODY;
        };
    }

    private static TaskAction primaryOf(TaskStatus status, boolean hasReviewRequest, RoundState round,
                                        boolean polled) {
        // The answer is what unblocks the session, whatever state-changing verb the status would advise.
        if (asking(status, round)) {
            return TaskAction.FOCUS;
        }
        return switch (status) {
            case NEW, IN_PROGRESS, SHIPPING -> TaskAction.FOCUS;
            // Shipping a round that changed nothing commits nothing, unless replies wait or no request exists yet.
            case REVIEW_PENDING -> switch (round.report()) {
                // Nothing polling means nothing will read the threads this round waits on, so the read is the move.
                case NO_CHANGES -> round.draftedReplies() || !hasReviewRequest ? TaskAction.SHIP
                        : polled ? null : TaskAction.SWEEP;
                // A question never reaches here: it is answered above, from whatever status it was asked.
                case QUESTION, PLAIN -> TaskAction.SHIP;
            };
            case CI_POLLING, CI_FAILED -> hasReviewRequest ? TaskAction.SWEEP : TaskAction.FOCUS;
            // Nothing is highlighted while a poller watches for the approval: a highlight would read as approved.
            case REVIEWED -> !hasReviewRequest ? TaskAction.DONE : polled ? null : TaskAction.SWEEP;
            // With no request there is nothing to land.
            case APPROVED -> hasReviewRequest ? TaskAction.DEPLOY : TaskAction.DONE;
            case DEPLOY_CONFLICT -> TaskAction.DEPLOY;
            case DEPLOYED -> TaskAction.DONE;
            // Not DONE: something was rolled back, so the expected next move is a fix, not a close.
            case REVERTED -> hasReviewRequest ? TaskAction.SHIP : TaskAction.FOCUS;
            case DONE -> null;
        };
    }

    /** One line of prose, for a hint line or a button tooltip. */
    private static String hint(TaskStatus status, boolean hasReviewRequest, RoundState round,
                               boolean agentSilent, boolean polled) {
        if (asking(status, round)) {
            // At REVIEW_PENDING the round is already back, so the answer is followed by a ship.
            return status == TaskStatus.REVIEW_PENDING
                    ? "read the round and the question it left (focus), then ship"
                    : "answer the question in the agent's window (focus)";
        }
        if (ownerOf(status) == Owner.AGENT && agentSilent) {
            return "agent stopped without reporting: focus to see why, or respawn";
        }
        return switch (status) {
            case NEW, IN_PROGRESS -> "agent is working; no action required";
            case REVIEW_PENDING -> switch (round.report()) {
                case NO_CHANGES -> !hasReviewRequest
                        ? "no code changed this round; ship opens the review request"
                        : round.draftedReplies()
                                ? "no code changed; ship posts the drafted replies and nothing else"
                                : polled
                                        ? "nothing to ship; the open threads are the reviewer's move"
                                        : "nothing is polling this round; sweep reads the comments and checks"
                                                + " now";
                case QUESTION, PLAIN -> "read the diff (ide), then ship";
            };
            case SHIPPING -> "agent is committing and pushing; wait";
            // A ship is not offered without a request (`FlowRules` gates it on one), so neither is it advised.
            case CI_POLLING -> !hasReviewRequest
                    ? "no review request on this task; focus the agent"
                    : polled
                            ? "waiting for comments and checks; sweep reads them now"
                            : "nothing is polling this round; sweep reads the comments and checks now";
            case CI_FAILED -> hasReviewRequest ? "sweep relays the failure to the agent"
                    : "no review request to read the failure from; focus the agent";
            // The checks are NOT asserted here: a red run on a round that came back clean leaves the status alone.
            case REVIEWED -> !hasReviewRequest
                    ? "nothing to approve: no request on this task; focus the agent, or done to close"
                    : polled
                            ? "nothing unresolved: waiting for an approval — deploy without one, or done"
                            : "nothing is polling for the approval; sweep reads it now";
            case APPROVED -> "approved: deploy or done";
            case DEPLOY_CONFLICT -> "resolve the conflict in the deploy worktree (ide), git add, then deploy";
            case DEPLOYED -> "done closes the task; ship again to deploy further changes";
            case REVERTED -> hasReviewRequest ? "the deploy was reverted: ship a fix, or done to close"
                    : "the deploy was reverted and no request is open; focus the agent";
            case DONE -> "closed";
        };
    }
}
