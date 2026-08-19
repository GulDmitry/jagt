package dev.jagt.orchestrator.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a task IS to a human and what may be done about it. Total over {@link TaskStatus} × {@link AgentReport},
 * so every surface offers the same set and an illegal move is never offered rather than merely rejected.
 * {@link #shippable} is the ONE ship rule, shared with the gate that executes it.
 *
 * <p>The report is an input because all three outcomes of a review round end at the same status, and only one
 * of them is a ship.
 *
 * <p>Liveness is NOT an input: it would cost a process spawn per task per render. A task stuck at
 * {@code SHIPPING} is therefore offered SHIP, and the gate refuses at execution time if its agent is alive.
 */
public record Move(Phase phase, Owner owner, List<TaskAction> actions, TaskAction primary, String hint) {

    /** Actions that make sense whatever the status: look at it, restart it, close it. */
    private static final List<TaskAction> ALWAYS = List.of(TaskAction.FOCUS, TaskAction.IDE, TaskAction.DIFF,
            TaskAction.RESPAWN, TaskAction.DONE);

    public static Move forTask(TaskStatus status, boolean hasReviewRequest, RoundState round) {
        List<TaskAction> actions = new ArrayList<>();
        if (shippable(status, false, hasReviewRequest)) {
            actions.add(TaskAction.SHIP);
        }
        if (hasReviewRequest) {
            actions.add(TaskAction.SWEEP);
        }
        if (deployable(status, hasReviewRequest)) {
            actions.add(TaskAction.DEPLOY);
        }
        // Offered whenever a deploy is the last thing that happened. Whether jagt KNOWS the merge commit is
        // not asked here (that would be a second input to the projection for one button); the gate refuses a
        // task deployed before jagt recorded it, with the by-hand command.
        if (status == TaskStatus.DEPLOYED) {
            actions.add(TaskAction.REVERT);
        }
        actions.addAll(ALWAYS);
        // Grouped, not interleaved: what moves the task on comes first, what only looks at it follows. Sorted
        // rather than hand-ordered so a new action lands on the right side of the card by declaring its group.
        actions.sort(Comparator.comparing(TaskAction::group));
        return new Move(phaseOf(status), ownerOf(status), List.copyOf(actions),
                primaryOf(status, hasReviewRequest, round), hint(status, round));
    }

    /**
     * Whether `ship` may proceed. {@code ship} IS the human's approval, so IN_PROGRESS and REVIEW_PENDING both
     * pass (agents often finish without self-reporting REVIEW_PENDING); a task stuck at SHIPPING whose agent
     * has DIED passes too (recovery), while a live SHIPPING agent means the ship is in flight. Once a review
     * request exists, CI_POLLING/CI_FAILED/DEPLOYED pass as well — deploy is a dev step, not an end state, so
     * the human iterates and ships another round onto the same request. Only NEW (nothing to ship onto) and
     * DONE (closed) are refused.
     */
    public static boolean shippable(TaskStatus status, boolean agentLive, boolean hasReviewRequest) {
        boolean anotherRound = hasReviewRequest && (status == TaskStatus.CI_POLLING
                || status == TaskStatus.CI_FAILED || status == TaskStatus.DEPLOYED
                // A reverted deploy is the one case where shipping again is the POINT: the change came back
                // out, the fix goes onto the same review request.
                || status == TaskStatus.REVERTED);
        return status == TaskStatus.REVIEW_PENDING || status == TaskStatus.IN_PROGRESS
                || (status == TaskStatus.SHIPPING && !agentLive) || anotherRound;
    }

    /**
     * WHAT A REVIEWER SAID IS NOT A GATE ON DEPLOY, and it never was below this line: `deploy` merges the task
     * BRANCH and git's only precondition is commits on it. So an open request is the rule, not the verdict — a
     * human looking at a REVIEW_PENDING card can land a request they have decided to land.
     *
     * <p>What is excluded is excluded because the deploy could only fail or race: NEW has nothing on the branch,
     * DONE is closed, SHIPPING is a push in flight, IN_PROGRESS is an agent committing INTO the branch this would
     * merge, and REVERTED has no commits the deploy branch does not already carry (a revert ADDS one), so it
     * could only ever answer "nothing to deploy". DEPLOY_CONFLICT is in whatever else is true: a stalled deploy
     * is finished by deploying again.
     */
    private static boolean deployable(TaskStatus status, boolean hasReviewRequest) {
        return switch (status) {
            case DEPLOY_CONFLICT -> true;
            case REVIEW_PENDING, CI_POLLING, CI_FAILED, REVIEWED, APPROVED, DEPLOYED -> hasReviewRequest;
            case NEW, IN_PROGRESS, SHIPPING, REVERTED, DONE -> false;
        };
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
            // Not unconditional: with no request there is nothing to land, and a primary the action list omits
            // would leave the board with no highlighted button at all.
            case REVIEWED, APPROVED -> hasReviewRequest ? TaskAction.DEPLOY : TaskAction.DONE;
            case DEPLOY_CONFLICT -> TaskAction.DEPLOY;
            case DEPLOYED -> TaskAction.DONE;
            // Not DONE: something was rolled back, so the expected next move is a fix, not a close.
            case REVERTED -> hasReviewRequest ? TaskAction.SHIP : TaskAction.FOCUS;
            case DONE -> null;
        };
    }

    /** One line of prose for the TUI and for a button tooltip — the same wording the dashboard always used. */
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
