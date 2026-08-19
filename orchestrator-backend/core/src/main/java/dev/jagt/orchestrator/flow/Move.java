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
 * <p>Liveness is projected as "not live": it would cost a process spawn per task per render. A task stuck at
 * {@code SHIPPING} is therefore offered SHIP, and the gate refuses at execution time if its agent is alive.
 */
public record Move(Phase phase, Owner owner, List<TaskAction> actions, TaskAction primary, String hint) {

    public static Move forTask(TaskStatus status, boolean hasReviewRequest, RoundState round) {
        return new Move(phaseOf(status), ownerOf(status),
                FlowRules.allowed(status, Facts.projected(hasReviewRequest)),
                primaryOf(status, hasReviewRequest, round), hint(status, round));
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

    /** Whose turn a status means. Public because a view that adds up time per owner must not map it a second way. */
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
            // CI_POLLING, where the poll relays the threads it just answered — unless replies are waiting,
            // because `ship` is the only thing that posts them.
            case REVIEW_PENDING -> switch (round.report()) {
                case NO_CHANGES -> round.draftedReplies() ? TaskAction.SHIP : null;
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
    private static String hint(TaskStatus status, RoundState round) {
        return switch (status) {
            case NEW, IN_PROGRESS -> "agent working — wait or focus";
            case REVIEW_PENDING -> switch (round.report()) {
                case NO_CHANGES -> round.draftedReplies()
                        ? "no code changed — ship to post the drafted replies, nothing else goes out"
                        : "nothing to ship — every comment answered; the open threads are the reviewer's move";
                case QUESTION -> "the agent is asking — answer it (focus), then ship";
                case PLAIN -> "your move: read the diff (ide), then ship";
            };
            case SHIPPING -> "shipping — agent committing/pushing; wait for the review request";
            case CI_POLLING -> "waiting on the code host — check the review when you want";
            case CI_FAILED -> "your move: check the review (it relays the failure)";
            case REVIEWED -> "your move: deploy or done";
            case APPROVED -> "approved — your move: deploy or done";
            case DEPLOY_CONFLICT -> "your move: ide opens the deploy worktree — resolve + git add, then deploy again";
            case DEPLOYED -> "your move: done — or ship more changes, then deploy again";
            case REVERTED -> "deploy reverted on the deploy branch — your move: fix and ship again, or done";
            case DONE -> "done";
        };
    }
}
