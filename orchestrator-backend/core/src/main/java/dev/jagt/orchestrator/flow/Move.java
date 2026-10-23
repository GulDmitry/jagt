package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.AutoReviewWatch;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What a task IS to a human: whose turn it is, which phase it reads as, what to do next and in what words.
 * Total over {@link TaskStatus} × {@link AgentReport}, the report being an input because all three outcomes of a
 * review round end at the same status. WHICH actions are legal is {@link FlowRules}. Liveness is projected as "not
 * live" for the rules: probing per task per render would cost a process spawn.
 */
public record Move(Phase phase, Owner owner, Attention attention, @Nullable String ask,
                   List<TaskAction> actions, @Nullable TaskAction primary, String hint) {

    public static Move forTask(TaskStatus status, Facts facts, RoundState round, boolean agentSilent) {
        return forTask(status, facts, round, agentSilent, AutoReviewWatch.none());
    }

    /** The WATCH rather than a flag off it: a promised poll that STOPPED and no poller at all are different cards. */
    public static Move forTask(TaskStatus status, Facts facts, RoundState round,
                               boolean agentSilent, AutoReviewWatch watch) {
        if (facts.gitStateChanged()) {
            return new Move(phaseOf(status), Owner.YOU, Attention.REQUIRED, "read what git changed",
                    FlowRules.allowed(status, facts), TaskAction.TRUST,
                    "git config or hooks changed since you trusted them; read the line, then trust");
        }
        if (facts.loopStopped()) {
            return new Move(phaseOf(status), Owner.YOU, Attention.REQUIRED, "lift the stop",
                    FlowRules.allowed(status, facts), TaskAction.LIFT,
                    "jagt stopped this task as a loop it suspects is its own bug; read the hover, then lift or close");
        }
        if (facts.repoRequested() && asking(status, round)) {
            return new Move(phaseOf(status), Owner.YOU, Attention.REQUIRED, "add the repository",
                    FlowRules.allowed(status, facts), TaskAction.ADD_REPO,
                    "the session asked for another repository: add it, or answer in the agent's window (focus)");
        }
        boolean hasReviewRequest = facts.hasReviewRequest();
        boolean polled = watch.state() == AutoReviewWatch.State.WATCHING;
        Owner owner = ownerOf(status, hasReviewRequest, round, agentSilent, watch.stopped());
        Attention attention = attentionOf(owner, status, round, polled);
        return new Move(phaseOf(status), owner, attention,
                ask(status, hasReviewRequest, round, agentSilent, attention),
                FlowRules.allowed(status, facts),
                primaryOf(status, hasReviewRequest, round, polled),
                hint(status, hasReviewRequest, round, agentSilent, polled));
    }

    private static Phase phaseOf(TaskStatus status) {
        return switch (status) {
            case NEW, PLAN_PENDING, IN_PROGRESS -> Phase.BUILD;
            case REVIEW_PENDING -> Phase.REVIEW;
            case VERIFYING, SHIPPING, CI_POLLING, CI_FAILED -> Phase.CHECK;
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
        if (masterReading(status, round)) {
            return Owner.AGENT;
        }
        Owner owner = waitingOn(status, hasReviewRequest, round);
        if (owner == Owner.AGENT && agentSilent && !FlowRules.awaitingVerification(status)) {
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
    private static @Nullable String ask(TaskStatus status, boolean hasReviewRequest, RoundState round, boolean agentSilent,
                              Attention attention) {
        if (attention == Attention.NONE) {
            return null;
        }
        String act = act(status, hasReviewRequest, round, agentSilent);
        return attention == Attention.OPTIONAL ? "you can " + act : act;
    }

    private static @Nullable String act(TaskStatus status, boolean hasReviewRequest, RoundState round,
                              boolean agentSilent) {
        if (asking(status, round)) {
            // A round already back is READ, not answered: the session that asked it has finished the round.
            return status == TaskStatus.REVIEW_PENDING ? "review the round" : "answer the session";
        }
        if (ownerOf(status) == Owner.AGENT && agentSilent) {
            return "check the stopped session";
        }
        return actOf(status, hasReviewRequest, round);
    }

    private static @Nullable String actOf(TaskStatus status, boolean hasReviewRequest, RoundState round) {
        return switch (status) {
            case REVIEW_PENDING -> reviewPendingAct(hasReviewRequest, round);
            case CI_POLLING -> hasReviewRequest ? "read the review" : "focus the agent";
            case CI_FAILED -> hasReviewRequest ? "relay the failed checks" : "focus the agent";
            case REVERTED -> hasReviewRequest ? "ship a fix" : "focus the agent";
            // REVIEWED is already read: nothing unresolved and the checks green, so only the approval is missing.
            case REVIEWED -> hasReviewRequest ? "check for the approval" : "close the task";
            case APPROVED -> hasReviewRequest ? "deploy it" : "close the task";
            case DEPLOY_CONFLICT -> "resolve the conflict";
            case PLAN_PENDING -> "read the plan";
            // Unreachable: both ways an agent gives these up are answered above.
            case NEW, IN_PROGRESS, VERIFYING, SHIPPING, DEPLOYED, DONE -> null;
        };
    }

    private static String reviewPendingAct(boolean hasReviewRequest, RoundState round) {
        return switch (round.report()) {
            case NO_CHANGES -> !hasReviewRequest ? "open the review request"
                    : round.draftedReplies() ? "post the drafted replies" : "read the review";
            case QUESTION, PLAIN -> "review and ship";
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
        if (FlowRules.outForReview(status) && !hasReviewRequest) {
            return Owner.YOU;
        }
        return ownerOf(status);
    }

    private static boolean masterReading(TaskStatus status, RoundState round) {
        return FlowRules.readByTheMaster(status) && round.masterReading();
    }

    private static boolean asking(TaskStatus status, RoundState round) {
        return asking(status, round.report());
    }

    /** A closed task's leftover message is not a question anybody still owes an answer to. */
    public static boolean asking(TaskStatus status, AgentReport report) {
        return report == AgentReport.QUESTION && status != TaskStatus.DONE;
    }

    /** A question put before the turn began was answered by the prompt that began it. */
    public static boolean endsUnreported(TaskStatus status) {
        return ownerOf(status) == Owner.AGENT && !FlowRules.awaitingVerification(status);
    }

    /** Whose turn a status ALONE means. */
    public static Owner ownerOf(TaskStatus status) {
        return switch (status) {
            case NEW, IN_PROGRESS, VERIFYING, SHIPPING -> Owner.AGENT;
            case PLAN_PENDING -> Owner.YOU;
            // REVIEWED is "nothing unresolved, checks green, NOT approved": handed in, waiting for a reviewer.
            case CI_POLLING, REVIEWED -> Owner.CI;
            case REVIEW_PENDING, CI_FAILED, APPROVED, DEPLOY_CONFLICT, REVERTED -> Owner.YOU;
            case DEPLOYED -> Owner.NOBODY;
            case DONE -> Owner.NOBODY;
        };
    }

    private static @Nullable TaskAction primaryOf(TaskStatus status, boolean hasReviewRequest, RoundState round,
                                        boolean polled) {
        // The answer is what unblocks the session, whatever state-changing verb the status would advise.
        if (asking(status, round)) {
            return TaskAction.FOCUS;
        }
        if (masterReading(status, round)) {
            return null;
        }
        return primaryOf(status, hasReviewRequest, round.report(), round.draftedReplies(), polled);
    }

    private static @Nullable TaskAction primaryOf(TaskStatus status, boolean hasReviewRequest, AgentReport report,
                                        boolean draftedReplies, boolean polled) {
        return switch (status) {
            case NEW, PLAN_PENDING, IN_PROGRESS, VERIFYING, SHIPPING -> TaskAction.FOCUS;
            case REVIEW_PENDING -> reviewPendingPrimary(hasReviewRequest, report, draftedReplies, polled);
            case CI_POLLING, CI_FAILED -> hasReviewRequest ? TaskAction.SWEEP : TaskAction.FOCUS;
            case REVIEWED -> reviewedPrimary(hasReviewRequest, polled);
            // With no request there is nothing to land.
            case APPROVED -> hasReviewRequest ? TaskAction.DEPLOY : TaskAction.DONE;
            case DEPLOY_CONFLICT -> TaskAction.DEPLOY;
            case DEPLOYED -> TaskAction.DONE;
            // Not DONE: something was rolled back, so the expected next move is a fix, not a close.
            case REVERTED -> hasReviewRequest ? TaskAction.SHIP : TaskAction.FOCUS;
            case DONE -> null;
        };
    }

    // Shipping a round that changed nothing commits nothing, unless replies wait or no request exists yet.
    private static @Nullable TaskAction reviewPendingPrimary(boolean hasReviewRequest, AgentReport report,
                                                   boolean draftedReplies, boolean polled) {
        return switch (report) {
            // Nothing polling means nothing will read the threads this round waits on, so the read is the move.
            case NO_CHANGES -> draftedReplies || !hasReviewRequest ? TaskAction.SHIP
                    : polled ? null : TaskAction.SWEEP;
            // A question never reaches here: it is answered above, from whatever status it was asked.
            case QUESTION, PLAIN -> TaskAction.SHIP;
        };
    }

    // Nothing is highlighted while a poller watches for the approval: a highlight would read as approved.
    private static @Nullable TaskAction reviewedPrimary(boolean hasReviewRequest, boolean polled) {
        if (!hasReviewRequest) {
            return TaskAction.DONE;
        }
        return polled ? null : TaskAction.SWEEP;
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
        if (masterReading(status, round)) {
            return "the Master is reading this round; no action required";
        }
        return switch (status) {
            case NEW, IN_PROGRESS -> "agent is working; no action required";
            case VERIFYING -> "jagt is running this project's checks";
            case PLAN_PENDING -> "a plan is waiting in `plan.md`; read it, then approve it";
            case REVIEW_PENDING -> reviewPendingHint(hasReviewRequest, round, polled);
            case SHIPPING -> "agent is committing and pushing; wait";
            case CI_POLLING -> ciPollingHint(hasReviewRequest, polled);
            case CI_FAILED -> hasReviewRequest ? "sweep relays the failure to the agent"
                    : "no review request to read the failure from; focus the agent";
            case REVIEWED -> reviewedHint(hasReviewRequest, polled);
            case APPROVED -> "approved: deploy or done";
            case DEPLOY_CONFLICT -> "resolve the conflict in the deploy worktree (ide), git add, then deploy";
            case DEPLOYED -> "done closes the task; ship again to deploy further changes";
            case REVERTED -> hasReviewRequest ? "the deploy was reverted: ship a fix, or done to close"
                    : "the deploy was reverted and no request is open; focus the agent";
            case DONE -> "closed";
        };
    }

    private static String reviewPendingHint(boolean hasReviewRequest, RoundState round, boolean polled) {
        return switch (round.report()) {
            case NO_CHANGES -> noChangesHint(hasReviewRequest, round.draftedReplies(), polled);
            case QUESTION, PLAIN -> "read the diff (ide), then ship";
        };
    }

    private static String noChangesHint(boolean hasReviewRequest, boolean draftedReplies, boolean polled) {
        if (!hasReviewRequest) {
            return "no code changed this round; ship opens the review request";
        }
        if (draftedReplies) {
            return "no code changed; ship posts the drafted replies and nothing else";
        }
        return polled ? "nothing to ship; the open threads are the reviewer's move" : notPollingHint();
    }

    // A ship is not offered without a request (`FlowRules` gates it on one), so neither is it advised.
    private static String ciPollingHint(boolean hasReviewRequest, boolean polled) {
        if (!hasReviewRequest) {
            return "no review request on this task; focus the agent";
        }
        return polled ? "waiting for comments and checks; sweep reads them now" : notPollingHint();
    }

    // The checks are NOT asserted here: a red run on a round that came back clean leaves the status alone.
    private static String reviewedHint(boolean hasReviewRequest, boolean polled) {
        if (!hasReviewRequest) {
            return "nothing to approve: no request on this task; focus the agent, or done to close";
        }
        return polled ? "nothing unresolved: waiting for an approval — deploy without one, or done"
                : "nothing is polling for the approval; sweep reads it now";
    }

    private static String notPollingHint() {
        return "nothing is polling this round; sweep reads the comments and checks now";
    }
}
