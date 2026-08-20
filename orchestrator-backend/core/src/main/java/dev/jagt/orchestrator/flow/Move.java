package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.flow.Phase;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.AutoReviewWatch;

import java.util.List;

/**
 * What a task IS to a human: whose turn it is, which phase it reads as, what to do next and in what words.
 * WHICH actions are legal is not decided here — {@link FlowRules} answers that for the gate as well, so a card
 * cannot advertise a move the gate then refuses. Total over {@link TaskStatus} × {@link AgentReport}.
 *
 * <p>The report is an input because all three outcomes of a review round end at the same status, and only one
 * of them is a ship.
 *
 * <p>Liveness is projected as "not live" for the RULES: probing per task per render would cost a process spawn,
 * so a task stuck at {@code SHIPPING} is offered SHIP and the gate refuses at execution time if its agent is
 * alive. {@code agentSilent} is the other direction and costs nothing here — the watchdog already probed and
 * stamped it, and a stopped agent must not read as a working one.
 */
public record Move(Phase phase, Owner owner, List<TaskAction> actions, TaskAction primary, String hint) {

    /** For a caller describing a task in a sentence rather than owning a card: it has no poller to speak for. */
    public static Move forTask(TaskStatus status, boolean hasReviewRequest, RoundState round,
                               boolean agentSilent) {
        return forTask(status, hasReviewRequest, round, agentSilent, AutoReviewWatch.none());
    }

    /**
     * The WATCH rather than a flag off it, because two different questions are asked of it and a boolean can only
     * answer one: whether a poll this task was promised has STOPPED (the wait becomes the human's), and whether
     * anything is polling AT ALL (nothing is, on an install with auto-review off — so the card must offer the
     * read as its next move instead of pointing at an approval nobody will ever fetch).
     */
    public static Move forTask(TaskStatus status, boolean hasReviewRequest, RoundState round,
                               boolean agentSilent, AutoReviewWatch watch) {
        boolean polled = watch.state() == AutoReviewWatch.State.WATCHING;
        return new Move(phaseOf(status), ownerOf(status, hasReviewRequest, round, agentSilent, watch.stopped()),
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

    /**
     * An agent that stopped is not working, whatever status it kept — whether it said so ({@code awaiting: …}) or
     * simply went quiet. The wait is the human's either way, and a card that still reads "agent" is dropped by
     * every filter and count that looks for their own move.
     */
    private static Owner ownerOf(TaskStatus status, boolean hasReviewRequest, RoundState round,
                                 boolean agentSilent, boolean pollStopped) {
        // ASKING IS STOPPING, whatever status it was asked from: the session waits until a human answers. Read
        // first because the statuses a question is NOT expected from are exactly the ones nothing else flips —
        // an agent asking while its task reads CI_POLLING or REVIEWED would otherwise reach nobody at all.
        if (asking(status, round)) {
            return Owner.YOU;
        }
        Owner owner = waitingOn(status, hasReviewRequest, round);
        if (owner == Owner.AGENT && agentSilent) {
            return Owner.YOU;
        }
        // A wait on the code host that nothing will look at again is a wait on the HUMAN: only a `sweep` of
        // theirs reads that request now. Read off the OWNER rather than off a set of statuses, so the round a
        // reviewer is sitting on cannot keep pointing at a poll that has stopped. An install that polls nothing
        // at all is NOT this case — it says so once per surface (`AutoReviewWatch.stopped`).
        return owner == Owner.CI && pollStopped ? Owner.YOU : owner;
    }

    /** Whose turn it is before liveness and the poller are taken into account. */
    private static Owner waitingOn(TaskStatus status, boolean hasReviewRequest, RoundState round) {
        // The only move left is a ship that commits nothing and hands the same threads back to the poller, so
        // the wait belongs to whoever writes the next comment — which takes a request for them to write it on.
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

    /**
     * Whose turn a status ALONE means. Public because a view that adds up time per owner must not map it a second
     * way, and it has only statuses to add up — which is also why a CARD can differ from it: the two cells that
     * need the round or the poller are not in a history of statuses, so a total booked from one is the closest
     * answer that data can give.
     */
    public static Owner ownerOf(TaskStatus status) {
        return switch (status) {
            case NEW, IN_PROGRESS, SHIPPING -> Owner.AGENT;
            // REVIEWED is "nothing unresolved, checks green, NOT approved" — work that is handed in and waiting
            // for a reviewer to come. Deploy stays offered for an install that needs no approval, but nothing
            // here is a move of the human's, and a card that claimed one teaches them to ignore the badge.
            case CI_POLLING, REVIEWED -> Owner.CI;
            case REVIEW_PENDING, CI_FAILED, APPROVED, DEPLOYED, DEPLOY_CONFLICT,
                 REVERTED -> Owner.YOU;
            case DONE -> Owner.NOBODY;
        };
    }

    private static TaskAction primaryOf(TaskStatus status, boolean hasReviewRequest, RoundState round,
                                        boolean polled) {
        // The answer is what unblocks the session, whatever the status would otherwise advise — and what it
        // would advise is a state-changing verb: SHIP on a round the agent said it cannot finish, DEPLOY on a
        // question about the thing being deployed.
        if (asking(status, round)) {
            return TaskAction.FOCUS;
        }
        return switch (status) {
            case NEW, IN_PROGRESS, SHIPPING -> TaskAction.FOCUS;
            // Shipping a round that changed nothing commits nothing and drops the task back into
            // CI_POLLING, where the poll relays the threads it just answered — unless replies are waiting
            // (`ship` is the only thing that posts them), or no request exists yet, where a ship opens one
            // instead of looping.
            case REVIEW_PENDING -> switch (round.report()) {
                // Nothing polling means nothing will read the threads this round is waiting on, so the read is
                // the move — exactly as it is at REVIEWED, and for the same reason.
                case NO_CHANGES -> round.draftedReplies() || !hasReviewRequest ? TaskAction.SHIP
                        : polled ? null : TaskAction.SWEEP;
                // A question never reaches here: it is answered above, from whatever status it was asked.
                case QUESTION, PLAIN -> TaskAction.SHIP;
            };
            case CI_POLLING, CI_FAILED -> hasReviewRequest ? TaskAction.SWEEP : TaskAction.FOCUS;
            // Nothing is highlighted while a poller is watching for the approval: deploy stays in the list for
            // whoever needs no approval, but highlighting it would read as the reviewer having answered. With
            // nothing polling — the window elapsed, or this install polls nothing at all — reading the request
            // IS the move, and a card with no highlighted button at all would be the alternative.
            case REVIEWED -> !hasReviewRequest ? TaskAction.DONE : polled ? null : TaskAction.SWEEP;
            // With no request there is nothing to land, and a primary the action list omits would leave a card
            // with no highlighted button at all.
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
            // At REVIEW_PENDING the round is already back, so the answer is followed by a ship; asked from
            // anywhere else, the question is the whole of what there is to say.
            return status == TaskStatus.REVIEW_PENDING
                    ? "answer the question (focus), then ship"
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
                // A question never reaches here — it is answered above, in the same words from every status.
                case QUESTION, PLAIN -> "read the diff (ide), then ship";
            };
            case SHIPPING -> "agent is committing and pushing; wait";
            // A ship is not offered without a request (`FlowRules` gates it on one), so neither is it advised.
            case CI_POLLING -> !hasReviewRequest
                    ? "no review request on this task; focus the agent"
                    : polled
                            ? "waiting for comments and checks; sweep reads them now"
                            : "nothing is polling this round; sweep reads the comments and checks now";
            case CI_FAILED -> "sweep relays the failure to the agent";
            // The checks are NOT asserted here: a red run on a round that already came back clean leaves the
            // status alone, and the dot beside the request is what says which way they went.
            case REVIEWED -> !hasReviewRequest
                    ? "nothing to approve: no request on this task; focus the agent, or done to close"
                    : polled
                            ? "nothing unresolved: waiting for an approval — deploy without one, or done"
                            : "nothing is polling for the approval; sweep reads it now";
            case APPROVED -> "approved: deploy or done";
            case DEPLOY_CONFLICT -> "resolve the conflict in the deploy worktree (ide), git add, then deploy";
            case DEPLOYED -> "done closes the task; ship again to deploy further changes";
            case REVERTED -> "the deploy was reverted: ship a fix, or done to close";
            case DONE -> "closed";
        };
    }
}
