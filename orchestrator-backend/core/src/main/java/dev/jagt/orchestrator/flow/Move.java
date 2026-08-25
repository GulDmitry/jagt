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
public record Move(Phase phase, Owner owner, Attention attention, String ask, List<TaskAction> actions,
                   TaskAction primary, String hint) {

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

    /**
     * Whether the human is INTERRUPTED or merely offered the next step. Read off the owner rather than a second
     * set of statuses, so a card can never be counted as needing them while its badge says otherwise. The quiet
     * tier is a good state whose next move is theirs whenever: an approval that landed, and a revert they made
     * themselves — shouting at somebody about the click they just made is what teaches them to stop reading the
     * badge, and the badge is the whole reason jagt has a board.
     *
     * <p>A question asked on a round that came back is the quiet tier too, while a poll is still reading that
     * round: comments keep arriving from people who are not the one being shouted at, and the next of them may
     * be the answer. Only there — anywhere else the question is in the agent's own window, where no comment on a
     * request can reach it — and only while the poll runs, since a stopped one leaves the round to this human.
     */
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
     * WHICH act is wanted, in the words a chip has room for — never the state it is in, which the status already
     * spells. It never names something the card cannot serve, and is null exactly when {@link Attention} is
     * {@code NONE}. Usually that is the highlighted verb; where the act is READING it is the card itself, whose
     * diff, drafted replies and session are all one click away.
     *
     * <p>The quiet tier says so in GRAMMAR rather than in colour, because a badge a human can only tell apart by
     * its shade is one they cannot tell apart at all: an interruption is an imperative, a move of theirs
     * whenever is offered.
     */
    private static String ask(TaskStatus status, boolean hasReviewRequest, RoundState round, boolean agentSilent,
                              Attention attention) {
        if (attention == Attention.NONE) {
            return null;
        }
        String act = act(status, hasReviewRequest, round, agentSilent);
        // A status that reached the tier without an act of its own falls to the invariant below rather than
        // wearing the word "null" on a card: null exactly when NONE is what MoveTest pins.
        if (act == null) {
            return null;
        }
        return attention == Attention.OPTIONAL ? "you can " + act : act;
    }

    private static String act(TaskStatus status, boolean hasReviewRequest, RoundState round,
                              boolean agentSilent) {
        if (asking(status, round)) {
            // A round already back is READ, not answered: the session that asked it has finished the round, and
            // its question is one more thing waiting on the card beside the diff and the drafted replies.
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
            // Unreachable: an agent owns these until it stops or asks, and both are answered above, while a
            // deployed or closed task waits on nobody at all.
            case NEW, IN_PROGRESS, SHIPPING, DEPLOYED, DONE -> null;
        };
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
            case REVIEW_PENDING, CI_FAILED, APPROVED, DEPLOY_CONFLICT, REVERTED -> Owner.YOU;
            // The change is live and closing is housekeeping with no clock on it, so a badge here wore the same
            // word as a stalled session and a deploy conflict — which is how a badge stops being read at all.
            case DEPLOYED -> Owner.NOBODY;
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
            case CI_FAILED -> hasReviewRequest ? "sweep relays the failure to the agent"
                    : "no review request to read the failure from; focus the agent";
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
            case REVERTED -> hasReviewRequest ? "the deploy was reverted: ship a fix, or done to close"
                    : "the deploy was reverted and no request is open; focus the agent";
            case DONE -> "closed";
        };
    }
}
