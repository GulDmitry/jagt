package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskLabel;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * One review sweep: read the request's approval + checks + unresolved comments, then take the single
 * correct action. Shared by the manual {@code sweep} command (human types it) and the
 * {@code AutoReviewScheduler} (unattended poll) so both behave identically — the code-review-must-have-a-
 * -human rule lives in the OUTCOME, not the trigger: an approval advances state, but comments are only
 * RELAYED to the agent as drafts (nothing is pushed/posted), so the human always closes the loop.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewSweepService {

    /** What the sweep did, so callers format a message / decide whether to notify. */
    public record SweepResult(Kind kind, String message) {
        public enum Kind { NO_MR, UNREADABLE, APPROVED, REVIEWED, PENDING, RELAYED, IN_FLIGHT }
    }

    private final ReviewReader reviewReader;
    private final AgentStatusReports statusReports;
    private final AgentSessions sessions;
    private final StateService stateService;
    /**
     * One sweep at a time per task, no matter who asked. The guard lives HERE, not in a caller, because
     * there are several triggers — the human's `sweep`, the auto-review scheduler, later a UI button — and
     * a second sweep of the same merge request spends a full headless read twice AND can relay a second
     * brief for the same review round (the agent then fixes the same comments twice, or interleaves them).
     * The scheduler keeps its own guard on top: that one stops polls from QUEUING up behind a slow sweep,
     * which is a different problem from two triggers colliding.
     */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public SweepResult sweep(String taskIdOrAlias) {
        // Resolve first: `sweep a1` and the scheduler's `sweep ABC-1` must take the SAME lock.
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        if (!inFlight.add(taskId)) {
            return new SweepResult(SweepResult.Kind.IN_FLIGHT,
                    "sweep " + taskId + ": already running — wait for it");
        }
        try {
            SweepResult result = sweepExclusively(taskId);
            String alias = stateService.task(taskId).map(TaskState::alias).orElse(null);
            log.atInfo().addKeyValue("task", taskId).addKeyValue("alias", alias)
                    .addKeyValue("outcome", result.kind())
                    .log("sweep {}: {}", TaskLabel.of(taskId, alias), result.message());
            return result;
        } finally {
            inFlight.remove(taskId);
        }
    }

    private SweepResult sweepExclusively(String taskId) {
        List<TaskRepo> reviewed = stateService.task(taskId).map(TaskState::repos).orElse(List.of()).stream()
                .filter(TaskRepo::hasReviewRequest)
                .toList();
        if (reviewed.isEmpty()) {
            return new SweepResult(SweepResult.Kind.NO_MR,
                    "error: no request linked to " + taskId + " — `ship` first");
        }
        // A repository with no request yet is work nobody is reviewing, so the round cannot be called clean:
        // advancing here would report "nothing unresolved" over a half that was never shipped.
        List<String> unshipped = stateService.task(taskId).map(TaskState::repos).orElse(List.of()).stream()
                .filter(repo -> !repo.hasReviewRequest())
                .map(TaskRepo::project)
                .toList();
        if (!unshipped.isEmpty()) {
            return new SweepResult(SweepResult.Kind.PENDING, "sweep " + taskId + ": no request in "
                    + String.join(", ", unshipped) + " — `ship` again");
        }
        String mrUrl = reviewed.stream().map(TaskRepo::mrUrl).collect(Collectors.joining(", "));
        // One unreadable request fails the WHOLE sweep, exactly as a partial read of one does: "nothing
        // unresolved + green" advances a task, and half a task's repositories cannot say that.
        List<ReviewFacts> rounds = new ArrayList<>();
        for (TaskRepo repo : reviewed) {
            Optional<ReviewFacts> read = reviewReader.read(taskId, repo.mrUrl());
            if (read.isEmpty() || !read.get().exists()) {
                return new SweepResult(SweepResult.Kind.UNREADABLE,
                        "error: could not read " + repo.mrUrl());
            }
            rounds.add(reviewed.size() == 1 ? read.get() : named(repo.project(), read.get()));
        }
        ReviewFacts r = merged(rounds);
        String pipeline = r.pipelineStatus() == null ? "" : r.pipelineStatus().toLowerCase();
        boolean pipelineFailed = pipeline.contains("fail");
        if (r.comments().isEmpty() && !pipelineFailed) {
            if (r.approved()) {
                statusReports.markApproved(taskId);
                return new SweepResult(SweepResult.Kind.APPROVED,
                        "sweep " + taskId + ": approved, checks " + r.pipelineStatus()
                                + " — `deploy` or `done`");
            }
            if (pipeline.contains("success")) {   // only advance when CI is GREEN, not merely still running
                statusReports.markReviewed(taskId);
                return new SweepResult(SweepResult.Kind.REVIEWED,
                        "sweep " + taskId + ": checks " + r.pipelineStatus()
                                + ", nothing unresolved — `deploy` or `done`");
            }
            return new SweepResult(SweepResult.Kind.PENDING,
                    "sweep " + taskId + ": checks " + r.pipelineStatus()
                            + ", nothing unresolved yet, not approved — waiting");
        }
        sessions.writeTaskContext(taskId, brief(mrUrl, r, pipelineFailed));
        return new SweepResult(SweepResult.Kind.RELAYED,
                "sweep " + taskId + ": " + r.comments().size() + " comment(s) relayed, checks "
                        + r.pipelineStatus());
    }

    /** Which repository a comment came from, so the agent knows which worktree to open. */
    private static ReviewFacts named(String project, ReviewFacts round) {
        return new ReviewFacts(round.exists(), round.approved(), round.pipelineStatus(),
                round.comments().stream().map(comment -> "[" + project + "] " + comment).toList());
    }

    /**
     * Several repositories, ONE round: the task is as far along as its least finished repository. Approved only
     * when every request is, and the pipeline is reported as the single worst one — a concatenation would read
     * as "success" to the caller's own check while one repository was still building.
     */
    private static ReviewFacts merged(List<ReviewFacts> rounds) {
        if (rounds.size() == 1) {
            return rounds.get(0);
        }
        return new ReviewFacts(true,
                rounds.stream().allMatch(ReviewFacts::approved),
                worstPipeline(rounds),
                rounds.stream().flatMap(round -> round.comments().stream()).toList());
    }

    private static String worstPipeline(List<ReviewFacts> rounds) {
        // A host with no checks at all answers "none", so the merged word stays one a human can read: an
        // empty slot in "checks , no unresolved comments" says nothing about which repository has none.
        List<String> statuses = rounds.stream()
                .map(round -> round.pipelineStatus() == null || round.pipelineStatus().isBlank()
                        ? "none" : round.pipelineStatus())
                .toList();
        return statuses.stream().filter(status -> status.toLowerCase().contains("fail")).findFirst()
                .orElseGet(() -> statuses.stream()
                        .filter(status -> !status.toLowerCase().contains("success"))
                        .findFirst()
                        .orElse("success"));
    }

    /**
     * The round is relayed as a JUDGEMENT, not as a work order. An agent handed a list of comments complies
     * with all of them — including the ones that are wrong about the system, which the reviewer cannot see
     * from the diff — and the human then reads agreement into code that was merely obedient. So the brief
     * spends its opening on the three routes a comment can take, and the reply file is what carries the
     * disagreements and the questions back.
     */
    private static String brief(String mrUrl, ReviewFacts r, boolean pipelineFailed) {
        StringBuilder brief = new StringBuilder("Review round for ").append(mrUrl).append(".\n");
        if (pipelineFailed) {
            brief.append("Checks: ").append(r.pipelineStatus()).append(" — fix the failing build.\n");
        }
        if (!r.comments().isEmpty()) {
            brief.append("""
                    <how_to_judge>
                    Your job this round is to get the code RIGHT, not to satisfy the reviewer. A comment is an
                    argument from someone who read the diff, not the system: it can be mistaken about the
                    architecture, and you have the code in front of you. Weigh each one, then take exactly ONE
                    route per comment:
                    - Right: fix it LOCALLY (no commit, no push).
                    - Wrong: change NOTHING and reply with the one concrete technical reason it is wrong.
                    - You cannot tell, or it is right but forces a design decision nobody gave you: do not guess
                      and do not half-implement it. Leave that comment's code alone, put the question in its
                      review_replies.md block, and hand the round back — notify_user, then set REVIEW_PENDING
                      with message "awaiting: <question, few words>".
                    Implementing a change you believe is wrong is the worst outcome available to you: silent
                    compliance is invisible in a diff. Never report a fix you did not make.
                    </how_to_judge>
                    <replies>
                    For EACH comment write a block in review_replies.md: the original comment (with its thread
                    link if available) followed by the reply you intend to post — including the ones you push
                    back on and the ones you are asking about. The file holds DRAFTS: post nothing and resolve
                    no thread this round. Nothing leaves this machine until the human ships.
                    </replies>
                    <comments>
                    """);
            r.comments().forEach(c -> brief.append("- ").append(c).append('\n'));
            brief.append("</comments>\n");
        }
        // An unanswered question ends the round rather than parking in it: staying CI_POLLING would have the
        // auto-review poll re-brief the agent on the very comments it was told to hold — every interval, each
        // one paying for another review read. REVIEW_PENDING is what "the human's move" means here, and the
        // question rides along in the status message.
        // With no comments this is a failed pipeline, and the agent that fixes it cannot push (below), so it
        // cannot watch the build turn green either — its exit is the local fix.
        // The round's OUTCOME rides in the message, because all three end at the same status and the human is
        // advised from it: a ship for a round that changed nothing only returns the task to CI_POLLING, where
        // the next poll relays the same threads (a reply does not resolve one) and the lap repeats.
        brief.append(r.comments().isEmpty()
                ? "When the build is fixed locally, set status REVIEW_PENDING."
                : """
                        When every comment is fixed, answered or asked about, set status REVIEW_PENDING, and open
                        the message with the outcome of THIS round:
                        - "awaiting: <question>" — a question of yours is still open.
                        - "no changes: <why, few words>" — you changed no code (all already handled, or you
                          pushed back on every comment). Never say this if you edited a file.
                        - anything else — you fixed code locally and there is a diff to read.""");
        brief.append("\nDo NOT push or post anything yourself.");
        return brief.toString();
    }
}
