package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.flow.Phase;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;

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
        return forTask(status, hasReviewRequest, round, agentSilent, false);
    }

    public static Move forTask(TaskStatus status, boolean hasReviewRequest, RoundState round,
                               boolean agentSilent, boolean pollStopped) {
        return new Move(phaseOf(status), ownerOf(status, hasReviewRequest, round, agentSilent, pollStopped),
                FlowRules.allowed(status, Facts.projected(hasReviewRequest)),
                primaryOf(status, hasReviewRequest, round),
                hint(status, hasReviewRequest, round, agentSilent, pollStopped));
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
        // The only move left is a ship that commits nothing and hands the same threads back to the poller, so
        // the wait belongs to whoever writes the next comment — which takes a request for them to write it on.
        if (status == TaskStatus.REVIEW_PENDING && hasReviewRequest
                && round.report() == AgentReport.NO_CHANGES && !round.draftedReplies()) {
            return Owner.CI;
        }
        // A poll this install makes for other tasks has stopped for this one, so it moves only if a human moves
        // it. An install that polls nothing says so once per surface, and does not turn every card into a task.
        if (status == TaskStatus.CI_POLLING && (pollStopped || !hasReviewRequest)) {
            return Owner.YOU;
        }
        Owner owner = ownerOf(status);
        return owner == Owner.AGENT && stopped(round, agentSilent) ? Owner.YOU : owner;
    }

    private static boolean stopped(RoundState round, boolean agentSilent) {
        return round.report() == AgentReport.QUESTION || agentSilent;
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
            case CI_POLLING -> Owner.CI;
            case REVIEW_PENDING, CI_FAILED, REVIEWED, APPROVED, DEPLOYED, DEPLOY_CONFLICT,
                 REVERTED -> Owner.YOU;
            case DONE -> Owner.NOBODY;
        };
    }

    private static TaskAction primaryOf(TaskStatus status, boolean hasReviewRequest, RoundState round) {
        return switch (status) {
            case NEW, IN_PROGRESS, SHIPPING -> TaskAction.FOCUS;
            // Shipping a round that changed nothing commits nothing and drops the task back into
            // CI_POLLING, where the poll relays the threads it just answered — unless replies are waiting
            // (`ship` is the only thing that posts them), or no request exists yet, where a ship opens one
            // instead of looping.
            case REVIEW_PENDING -> switch (round.report()) {
                case NO_CHANGES -> round.draftedReplies() || !hasReviewRequest ? TaskAction.SHIP : null;
                case QUESTION -> TaskAction.FOCUS;
                case PLAIN -> TaskAction.SHIP;
            };
            case CI_POLLING, CI_FAILED -> hasReviewRequest ? TaskAction.SWEEP : TaskAction.FOCUS;
            // With no request there is nothing to land, and a primary the action list omits would leave a card
            // with no highlighted button at all.
            case REVIEWED, APPROVED -> hasReviewRequest ? TaskAction.DEPLOY : TaskAction.DONE;
            case DEPLOY_CONFLICT -> TaskAction.DEPLOY;
            case DEPLOYED -> TaskAction.DONE;
            // Not DONE: something was rolled back, so the expected next move is a fix, not a close.
            case REVERTED -> hasReviewRequest ? TaskAction.SHIP : TaskAction.FOCUS;
            case DONE -> null;
        };
    }

    /** One line of prose, for a hint line or a button tooltip. */
    private static String hint(TaskStatus status, boolean hasReviewRequest, RoundState round,
                               boolean agentSilent, boolean pollStopped) {
        if (ownerOf(status) == Owner.AGENT && stopped(round, agentSilent)) {
            return round.report() == AgentReport.QUESTION
                    ? "answer the question in the agent's window (focus)"
                    : "agent stopped without reporting: focus to see why, or respawn";
        }
        return switch (status) {
            case NEW, IN_PROGRESS -> "agent is working; no action required";
            case REVIEW_PENDING -> switch (round.report()) {
                case NO_CHANGES -> !hasReviewRequest
                        ? "no code changed this round; ship opens the review request"
                        : round.draftedReplies()
                                ? "no code changed; ship posts the drafted replies and nothing else"
                                : "nothing to ship";
                case QUESTION -> "answer the question (focus), then ship";
                case PLAIN -> "read the diff (ide), then ship";
            };
            case SHIPPING -> "agent is committing and pushing; wait";
            case CI_POLLING -> !hasReviewRequest
                    ? "no review request on this task; focus the agent, or ship to open one"
                    : pollStopped
                            ? "nothing polls this round any more; sweep reads the comments and checks now"
                            : "waiting for comments and checks; sweep reads them now";
            case CI_FAILED -> "sweep relays the failure to the agent";
            case REVIEWED -> "no open comments, checks green: deploy or done";
            case APPROVED -> "approved: deploy or done";
            case DEPLOY_CONFLICT -> "resolve the conflict in the deploy worktree (ide), git add, then deploy";
            case DEPLOYED -> "done closes the task; ship again to deploy further changes";
            case REVERTED -> "the deploy was reverted: ship a fix, or done to close";
            case DONE -> "closed";
        };
    }
}
