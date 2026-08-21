package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.task.TaskLabel;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What an agent says about its own task, and the one ping the human gets for it. WHICH task a caller may touch is
 * already decided upstream.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentStatusReports {

    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final int MAX_MESSAGE = 100;

    private final StateService stateService;
    private final Notifications notifications;
    private final FlowReports flow;
    private final WorktreeChanges worktreeChanges;

    /** For jagt's own reports, which carry no outcome of an agent's and no request to link. */
    public String report(String status, String message, String taskId) {
        return report(status, message, null, null, taskId);
    }

    /**
     * @param outcome          what this report SAYS about a review round, from the tool's own vocabulary; null
     *                         falls back to the marker the message opens with
     * @param reviewRequestUrl the request this report is about, instead of one scraped out of the message
     */
    public String report(String status, String message, String outcome, String reviewRequestUrl,
                         String taskId) {
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status '" + status + "'. Allowed: "
                    + List.of(TaskStatus.values()));
        }
        Optional<TaskState> current = stateService.task(taskId);
        String shortMessage = abbreviate(stated(message, outcome, current));
        // A message is cut down to one dashboard line, and a cut URL is a dead link. The named request wins: a
        // url is a fact, and finding it in prose is a guess about where the agent put it.
        String url = reviewRequestUrl == null || reviewRequestUrl.isBlank()
                ? extractUrl(message)
                : reviewRequestUrl.strip();
        // The dashboard is the SSOT for "where is my request" — a linkless CI_POLLING is a lie. An agent with
        // a question hands the round back at REVIEW_PENDING instead, where the question IS what the board shows.
        if (newStatus == TaskStatus.CI_POLLING && url == null) {
            throw new IllegalArgumentException(
                    "CI_POLLING requires the request link in the message, e.g."
                            + " \"review request: https://...\"");
        }
        TaskStatus previous = current.map(TaskState::status).orElse(null);
        // What the machine lets this report land on: a status the human owns keeps the task where it is, and the
        // agent is told so rather than left reading its own word back.
        TaskStatus landed = previous == null ? newStatus : FlowRules.reported(previous, newStatus);
        boolean updated = flow.report(taskId, newStatus, shortMessage, (was, next) -> {
            if (url == null) {
                return next;
            }
            // A task repeating CI_POLLING on the request it already carries is the same round; entering the
            // status, or naming another request, hands a new one over.
            boolean sameRound = was == TaskStatus.CI_POLLING && url.equals(next.mrUrl());
            return next.status() == TaskStatus.CI_POLLING && !sameRound
                    ? next.withReviewRound(url)
                    : next.withMrUrl(url);
        });
        if (!updated) {
            throw new IllegalArgumentException("Task " + taskId + " not found in state.json");
        }
        String alias = current.map(TaskState::alias).orElse(null);
        // An agent that stops to ask usually keeps its status, so the message is the only thing that changed —
        // and nobody is watching its window.
        boolean askedNow = AgentReport.of(shortMessage) == AgentReport.QUESTION
                && AgentReport.of(current.map(TaskState::message).orElse(null)) != AgentReport.QUESTION;
        if (landed != previous) {
            log.atInfo().addKeyValue("task", taskId).addKeyValue("alias", alias)
                    .addKeyValue("status", landed).addKeyValue("from", previous)
                    .log("<- agent {}: {}{}", TaskLabel.of(taskId, alias), landed,
                            shortMessage == null ? "" : " — " + shortMessage);
        } else if (askedNow) {
            log.atInfo().addKeyValue("task", taskId).addKeyValue("alias", alias)
                    .addKeyValue("status", landed)
                    .log("<- agent {}: {}", TaskLabel.of(taskId, alias), shortMessage);
        }
        // A keep-alive says nothing new; a task handing control back, or stopping to ask, does.
        boolean handedBack = landed != previous
                && (landed == TaskStatus.REVIEW_PENDING || landed == TaskStatus.CI_FAILED);
        if (handedBack || askedNow) {
            // Re-read: the same call may have LINKED the request, and the advice differs on whether one exists.
            // `current` is only what the task said before, which is what askedNow needed.
            ping(taskId, landed, shortMessage, stateService.task(taskId));
        }
        if (landed != newStatus) {
            return "Task " + taskId + " stays " + landed + ": that one is a human's to move on from. Your line"
                    + " was recorded" + (shortMessage == null ? "" : " (" + shortMessage + ")");
        }
        return "Task " + taskId + " -> " + landed + (shortMessage == null ? "" : " (" + shortMessage + ")");
    }

    /**
     * A clean review (CI green, nothing unresolved) IS a transition: another round stops being the next move. It
     * is not an approval, so nobody is interrupted for it — see {@link #ping}.
     */
    public void markReviewed(String taskId) {
        markOutcome(taskId, TaskStatus.REVIEWED, "reviewed — checks green, no unresolved comments");
    }

    /** A real approval by a human, not merely "nothing left to address". */
    public void markApproved(String taskId) {
        markOutcome(taskId, TaskStatus.APPROVED, "approved — checks green, request approved");
    }

    public String notifyUser(String title, String message) {
        notifications.send(Notification.fromAgent(null, title, message));
        return "Notification sent";
    }

    /**
     * A round that is polled while it waits reads the same outcome every interval, and reporting it again would
     * rewrite the task's message — an agent's `awaiting: …` among it — clear the watchdog's silence stamp and
     * stamp activity for a session that has not spoken. So an unchanged status is not reported at all.
     */
    private void markOutcome(String taskId, TaskStatus status, String message) {
        String id = stateService.canonicalTaskId(taskId);
        TaskStatus previous = stateService.task(id).map(TaskState::status).orElse(null);
        if (status == previous) {
            return;
        }
        if (flow.report(id, status, message)) {
            ping(id, status, message, stateService.task(id));
        }
    }

    /**
     * The human is tapped for a move of THEIRS and nothing else: a round that came back clean but unapproved, or
     * one whose threads are the reviewer's to close, is news nobody can act on — and a notification that asks for
     * nothing is what teaches them to dismiss the ones that do. Which is exactly the question the projection
     * answers, so the ping reads it rather than keeping a second list of statuses worth interrupting for.
     */
    private void ping(String taskId, TaskStatus status, String message, Optional<TaskState> task) {
        RoundState round = RoundState.of(message,
                task.map(t -> WorktreeFiles.draftedReplies(t, status)).orElse(false));
        // Not silent: whoever this ping is about has just spoken, or jagt has just read the round for it.
        Move move = Move.forTask(status, task.map(TaskState::hasReviewRequest).orElse(true), round, false);
        if (move.owner() != Owner.YOU) {
            return;
        }
        notifications.send(Notification.fromAgent(taskId, title(status, round),
                banner(move.hint(), round)));
    }

    /** A question is what the human has to act on; which status it was asked from is not. */
    private static String title(TaskStatus status, RoundState round) {
        return round.report() == AgentReport.QUESTION
                ? "needs input"
                : status.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The drafted replies are named here because a notification carries nothing else that would show them — and
     * after a round that changed nothing, the advice is already about posting them.
     */
    private static String banner(String hint, RoundState round) {
        return round.draftedReplies() && round.report() != AgentReport.NO_CHANGES
                ? hint + " — drafted replies wait in review_replies.md"
                : hint;
    }

    /**
     * What this report SAYS about a round, written into the message as the one marker `AgentReport` parses — the
     * typed argument first, the agent's own opening as the fallback for a session briefed before it existed.
     *
     * <p>NO_CHANGES is then CHECKED: "I changed nothing" is the one claim jagt can measure, and a round that
     * edited files is a diff for the human to read whatever it called itself. Nothing else here is verifiable —
     * a question is a question because the agent says so.
     */
    private String stated(String message, String outcome, Optional<TaskState> task) {
        AgentReport claimed = claimed(outcome, message);
        String detail = AgentReport.withoutMarker(message);
        if (claimed == AgentReport.NO_CHANGES && task.filter(worktreeChanges::anyUncommitted).isPresent()) {
            log.atInfo().addKeyValue("task", task.get().alias())
                    .log("report says no changes, but the worktree has uncommitted work — recorded as a round"
                            + " with a diff");
            claimed = AgentReport.PLAIN;
        }
        return switch (claimed) {
            case QUESTION -> marked("awaiting", detail);
            case NO_CHANGES -> marked("no changes", detail);
            case PLAIN -> message == null ? null : detail;
        };
    }

    private static AgentReport claimed(String outcome, String message) {
        if (outcome == null || outcome.isBlank()) {
            return AgentReport.of(message);
        }
        return switch (outcome.strip().toLowerCase()) {
            case "question" -> AgentReport.QUESTION;
            case "no_changes" -> AgentReport.NO_CHANGES;
            default -> AgentReport.of(message);
        };
    }

    private static String marked(String marker, String detail) {
        return detail.isBlank() ? marker : marker + ": " + detail;
    }

    private static String extractUrl(String text) {
        if (text == null) {
            return null;
        }
        var matcher = URL.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    /** One dashboard line: a status message is a headline, and an agent's essay ruins the table. */
    private static String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        String flat = message.replaceAll("\\s+", " ").strip();
        return flat.length() <= MAX_MESSAGE ? flat : flat.substring(0, MAX_MESSAGE - 3) + "...";
    }
}
