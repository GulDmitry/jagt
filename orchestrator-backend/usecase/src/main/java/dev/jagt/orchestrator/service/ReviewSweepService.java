package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.notify.Notifications;

import dev.jagt.orchestrator.port.Notification;

import dev.jagt.orchestrator.flow.Pipeline;

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
 * One review sweep. The human-in-the-loop rule lives in the OUTCOME, not in who triggered it: an approval
 * advances state, but comments are only RELAYED to the agent as drafts — nothing is pushed or posted, so the
 * human always closes the loop.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewSweepService {

    public record SweepResult(Kind kind, String message) {
        public enum Kind { NO_MR, UNREADABLE, APPROVED, REVIEWED, PENDING, RELAYED, IN_FLIGHT }
    }

    private final ReviewReader reviewReader;
    private final AgentStatusReports statusReports;
    private final AgentSessions sessions;
    private final StateService stateService;
    private final Notifications notifications;
    /**
     * One sweep at a time per task, no matter who asked. The guard lives HERE rather than in a caller because
     * several triggers reach it, and a second sweep of one request pays for the read twice AND relays a second
     * brief for the same review round (the agent then fixes the same comments twice, or interleaves them).
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
        record(taskId, r);
        Pipeline checks = Pipeline.of(r.pipelineStatus());
        boolean pipelineFailed = checks == Pipeline.RED;
        if (r.comments().isEmpty() && !pipelineFailed) {
            if (r.approved()) {
                statusReports.markApproved(taskId);
                return new SweepResult(SweepResult.Kind.APPROVED,
                        "sweep " + taskId + ": approved, checks " + r.pipelineStatus()
                                + " — `deploy` or `done`");
            }
            if (checks == Pipeline.GREEN) {
                statusReports.markReviewed(taskId);
                return new SweepResult(SweepResult.Kind.REVIEWED,
                        "sweep " + taskId + ": checks " + r.pipelineStatus()
                                + ", nothing unresolved — waiting for an approval; `deploy` without one");
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

    /**
     * Keeps what the host said about this round — the checks, whether it is approved, when it says the request was
     * opened — and taps the human ONCE when a run turns red: a later poll saying the same thing writes nothing and
     * says nothing, or an unattended sweep would rewrite the state file and notify on a loop. ONE write for all
     * three, since all three come off one read. The approval is STAMPED rather than left to the status, because
     * every surface shows it beside the request from CI_POLLING on, long before any status could carry it.
     */
    private void record(String taskId, ReviewFacts facts) {
        Optional<TaskState> before = stateService.task(taskId);
        String said = before.map(TaskState::pipelineStatus).orElse(null);
        boolean newChecks = !java.util.Objects.equals(said, facts.pipelineStatus());
        boolean newApproval = !java.util.Objects.equals(before.map(TaskState::approved).orElse(null),
                facts.approved());
        boolean newOpened = facts.openedAt() > 0
                && before.map(TaskState::requestOpenedAt).orElse(0L) != facts.openedAt();
        if (!newChecks && !newApproval && !newOpened) {
            return;
        }
        stateService.updateTask(taskId, task -> task.withPipelineStatus(facts.pipelineStatus())
                .withApproved(facts.approved()).withRequestOpenedAt(facts.openedAt()));
        Pipeline was = Pipeline.of(said);
        Pipeline now = Pipeline.of(facts.pipelineStatus());
        if (newChecks && now.worthATap() && now != was) {
            notifications.send(Notification.checksFailed(taskId, facts.pipelineStatus()));
        }
    }

    private static ReviewFacts named(String project, ReviewFacts round) {
        return new ReviewFacts(round.exists(), round.approved(), round.pipelineStatus(),
                round.comments().stream().map(comment -> "[" + project + "] " + comment).toList(),
                round.openedAt());
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
                rounds.stream().flatMap(round -> round.comments().stream()).toList(),
                longestOpen(rounds));
    }

    /** The OLDEST request: how long the review has been hanging is the longest any of them has waited. */
    private static long longestOpen(List<ReviewFacts> rounds) {
        return rounds.stream().mapToLong(ReviewFacts::openedAt).filter(opened -> opened > 0).min().orElse(0);
    }

    /**
     * The worst repository's own wording. Ordered by VERDICT rather than by the words themselves, so the sweep's
     * decision and what a surface shows cannot disagree about one read; a host with no checks answers "none", so
     * the merged line stays something a human can read.
     */
    private static String worstPipeline(List<ReviewFacts> rounds) {
        return rounds.stream()
                .map(round -> round.pipelineStatus() == null || round.pipelineStatus().isBlank()
                        ? "none" : round.pipelineStatus())
                .min(java.util.Comparator.comparingInt(said -> Pipeline.of(said).severity()))
                .orElse("none");
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
                    review_replies.md is what the human READS to approve this round — end to end, in one pass,
                    before anything is posted. Write ONE block per comment, in this shape and nothing else:

                    ## <thread link, or file:line>
                    > <the comment, trimmed to the sentence that matters>
                    FIXED | NO CHANGE | QUESTION - <the reply, one or two sentences>

                    The verdict word is for the human; what follows the dash is posted verbatim. Every comment
                    gets a block, including the ones you push back on and the ones you are asking about.

                    NECESSARY AND SUFFICIENT is the test for every line: drop it if the answer survives without
                    it, and answer completely with what is left. No restating the comment beyond the quoted
                    line, no thanks, no re-describing the diff, no test or build status, no headers or bullets
                    inside a reply. If the file is longer than the diff it explains, it is wrong.

                    The file holds DRAFTS: post nothing and resolve no thread this round. Nothing leaves this
                    machine until the human ships.
                    </replies>
                    <comments>
                    """);
            r.comments().forEach(c -> brief.append("- ").append(c).append('\n'));
            brief.append("</comments>\n");
        }
        // An unanswered question ENDS the round rather than parking in it: staying CI_POLLING would have the
        // auto-review poll re-brief the agent on the very comments it was told to hold, paying for another
        // review read every interval. With no comments this is a failed pipeline, and an agent that cannot push
        // cannot watch the build turn green either — its exit is the local fix.
        // The round's OUTCOME rides in the message, because all three end at the same status: a ship for a round
        // that changed nothing only returns the task to CI_POLLING, where the next poll relays the same threads
        // (a reply does not resolve one) and the lap repeats.
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
