package dev.jagt.orchestrator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * What a task IS to a human and what may be done about it: its {@link Phase}, whose turn it is, the actions
 * that are legal right now, and which of them is the obvious one. Pure and total over {@link TaskStatus} and {@link AgentReport}, so
 * every surface — the TUI, the web board, the HTTP API — offers the same set, and an illegal move is not
 * merely rejected but never offered.
 *
 * <p>This replaces a prose hint that could be neither turned into a button nor validated, and that advised
 * independently of the gates in {@code OrchestratorTools} (two sources of truth for "what can I do now").
 * {@link #shippable} is now that one rule, used both here and by the ship gate itself.
 *
 * <p>The status alone cannot answer "whose move is it": a review round ends at REVIEW_PENDING whether the agent
 * fixed code, asked something, or found nothing to change, and advising a ship for all three turned the third
 * into a loop — the ship returns the task to CI_POLLING, where the poll relays the threads it just answered.
 *
 * <p>Liveness is deliberately NOT an input to the projection: asking tmux whether each agent is alive would
 * mean a process spawn per task on every render. A task stuck at {@code SHIPPING} is therefore offered SHIP,
 * and if its agent turns out to be alive the gate refuses at execution time — the honest split, since only the
 * execution path can afford to look.
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
        if (deployable(status)) {
            actions.add(TaskAction.DEPLOY);
        }
        // Offered whenever a deploy is the last thing that happened. Whether jagt KNOWS the merge commit is
        // not asked here (that would be a second input to the projection for one button); the gate refuses a
        // task deployed before jagt recorded it, with the by-hand command.
        if (status == TaskStatus.DEPLOYED) {
            actions.add(TaskAction.REVERT);
        }
        actions.addAll(ALWAYS);
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

    private static boolean deployable(TaskStatus status) {
        return status == TaskStatus.REVIEWED || status == TaskStatus.APPROVED
                || status == TaskStatus.DEPLOY_CONFLICT;
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

    private static Owner ownerOf(TaskStatus status) {
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
            case REVIEWED, APPROVED, DEPLOY_CONFLICT -> TaskAction.DEPLOY;
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
