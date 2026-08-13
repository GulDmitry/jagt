package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.ReviewFacts;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One MR review sweep: read the MR's approval + pipeline + unresolved comments, then take the single
 * correct action. Shared by the manual {@code review} command (human types it) and the
 * {@code AutoReviewScheduler} (unattended poll) so both behave identically — the code-review-must-have-a-
 * -human rule lives in the OUTCOME, not the trigger: an approval advances state, but comments are only
 * RELAYED to the agent as drafts (nothing is pushed/posted), so the human always closes the loop.
 */
@Service
public class ReviewSweepService {

    /** What the sweep did, so callers format a message / decide whether to notify. */
    public record SweepResult(Kind kind, String message) {
        public enum Kind { NO_MR, UNREADABLE, APPROVED, REVIEWED, PENDING, RELAYED, IN_FLIGHT }
    }

    private final ReviewReader reviewReader;
    private final OrchestratorTools tools;
    private final StateService stateService;
    /**
     * One sweep at a time per task, no matter who asked. The guard lives HERE, not in a caller, because
     * there are several triggers — the human's `review`, the auto-review scheduler, later a UI button — and
     * a second sweep of the same merge request spends a full headless read twice AND can relay a second
     * brief for the same review round (the agent then fixes the same comments twice, or interleaves them).
     * The scheduler keeps its own guard on top: that one stops polls from QUEUING up behind a slow sweep,
     * which is a different problem from two triggers colliding.
     */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public ReviewSweepService(ReviewReader reviewReader, OrchestratorTools tools, StateService stateService) {
        this.reviewReader = reviewReader;
        this.tools = tools;
        this.stateService = stateService;
    }

    public SweepResult sweep(String taskIdOrAlias) {
        // Resolve first: `review a1` and the scheduler's `review ABC-1` must take the SAME lock.
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        if (!inFlight.add(taskId)) {
            return new SweepResult(SweepResult.Kind.IN_FLIGHT,
                    "review " + taskId + ": a sweep is already running — wait for it to finish");
        }
        try {
            return sweepExclusively(taskId);
        } finally {
            inFlight.remove(taskId);
        }
    }

    private SweepResult sweepExclusively(String taskId) {
        String mrUrl = tools.taskMrUrl(taskId);
        if (mrUrl == null || mrUrl.isBlank()) {
            return new SweepResult(SweepResult.Kind.NO_MR,
                    "error: no MR linked to " + taskId + " — `ship` or `resume <mr-url>` first");
        }
        Optional<ReviewFacts> facts = reviewReader.read(taskId, mrUrl);
        if (facts.isEmpty() || !facts.get().exists()) {
            return new SweepResult(SweepResult.Kind.UNREADABLE,
                    "error: could not read the MR review for " + mrUrl);
        }
        ReviewFacts r = facts.get();
        String pipeline = r.pipelineStatus() == null ? "" : r.pipelineStatus().toLowerCase();
        boolean pipelineFailed = pipeline.contains("fail");
        if (r.comments().isEmpty() && !pipelineFailed) {
            if (r.approved()) {
                tools.markApproved(taskId);
                return new SweepResult(SweepResult.Kind.APPROVED,
                        "review " + taskId + ": MR approved, pipeline " + r.pipelineStatus()
                                + " — your move: `deploy` or `done`");
            }
            if (pipeline.contains("success")) {   // only advance when CI is GREEN, not merely still running
                tools.markReviewed(taskId);
                return new SweepResult(SweepResult.Kind.REVIEWED,
                        "review " + taskId + ": pipeline " + r.pipelineStatus()
                                + ", no unresolved comments — your move: `deploy` or `done`");
            }
            return new SweepResult(SweepResult.Kind.PENDING,
                    "review " + taskId + ": pipeline " + r.pipelineStatus()
                            + ", no unresolved comments yet, not approved — still waiting");
        }
        tools.writeTaskContext(taskId, brief(mrUrl, r, pipelineFailed));
        return new SweepResult(SweepResult.Kind.RELAYED,
                "review " + taskId + ": relayed " + r.comments().size() + " comment(s), pipeline "
                        + r.pipelineStatus() + " -> agent");
    }

    private static String brief(String mrUrl, ReviewFacts r, boolean pipelineFailed) {
        StringBuilder brief = new StringBuilder("Review round for MR ").append(mrUrl).append(".\n");
        if (pipelineFailed) {
            brief.append("Pipeline: ").append(r.pipelineStatus()).append(" — fix the failing build.\n");
        }
        if (!r.comments().isEmpty()) {
            brief.append("Unresolved comments — fix the valid ones LOCALLY (no commit/push). For EACH"
                    + " comment write a block in review_replies.md: the original comment (with its thread"
                    + " link if available) followed by the reply you intend to post:\n");
            r.comments().forEach(c -> brief.append("- ").append(c).append('\n'));
        }
        brief.append("When done, set status REVIEW_PENDING. Do NOT push or post anything yourself.");
        return brief.toString();
    }
}
