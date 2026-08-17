package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.Move;
import dev.jagt.orchestrator.model.RoundState;
import dev.jagt.orchestrator.model.TaskLabel;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.UserNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What an agent says about its own task, and the one ping the human gets for it. Statuses arrive here from the
 * agent's MCP call and from the review sweep; the caller has already decided WHICH task it may touch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentStatusReports {

    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final int MAX_MESSAGE = 100;

    private final StateService stateService;
    private final UserNotifier userNotifier;

    public String report(String status, String message, String taskId) {
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status '" + status + "'. Allowed: "
                    + List.of(TaskStatus.values()));
        }
        String shortMessage = abbreviate(message);
        Optional<TaskState> current = stateService.task(taskId);
        // A message is cut down to one dashboard line, and a cut URL is a dead link.
        String url = extractUrl(message);
        // The dashboard is the SSOT for "where is my MR" — a linkless CI_POLLING is a lie. An agent with a
        // question hands the round back at REVIEW_PENDING instead, where the question IS what the board shows.
        if (newStatus == TaskStatus.CI_POLLING && url == null) {
            throw new IllegalArgumentException(
                    "CI_POLLING requires the MR link in the message, e.g. \"MR: https://...\"");
        }
        TaskStatus previous = current.map(TaskState::status).orElse(null);
        boolean updated = stateService.updateTask(taskId, t -> {
            TaskState next = t.withStatus(newStatus, shortMessage);
            if (url != null) {
                next = next.withMrUrl(url);
                // First time an MR is linked = the auto-review window start; never reset it on later rounds.
                if (t.mrCreatedAt() == 0) {
                    next = next.withMrCreatedAt(System.currentTimeMillis());
                }
            }
            return next;
        });
        if (!updated) {
            throw new IllegalArgumentException("Task " + taskId + " not found in state.json");
        }
        if (newStatus != previous) {
            String alias = current.map(TaskState::alias).orElse(null);
            log.atInfo().addKeyValue("task", taskId).addKeyValue("alias", alias)
                    .addKeyValue("status", newStatus).addKeyValue("from", previous)
                    .log("<- agent {}: {}{}", TaskLabel.of(taskId, alias), newStatus,
                            shortMessage == null ? "" : " — " + shortMessage);
            // A keep-alive says nothing new; a task handing control back does.
            if (newStatus == TaskStatus.REVIEW_PENDING || newStatus == TaskStatus.CI_FAILED) {
                ping(taskId, newStatus, shortMessage, current);
            }
        }
        return "Task " + taskId + " -> " + newStatus + (shortMessage == null ? "" : " (" + shortMessage + ")");
    }

    /**
     * A clean review (CI green, nothing unresolved) IS a transition: the next move becomes deploy/done instead
     * of looping back to review.
     */
    public void markReviewed(String taskId) {
        markOutcome(taskId, TaskStatus.REVIEWED, "reviewed — CI green, no unresolved comments");
    }

    /** A real approval by a human, not merely "nothing left to address". */
    public void markApproved(String taskId) {
        markOutcome(taskId, TaskStatus.APPROVED, "approved — CI green, MR approved");
    }

    public String notifyUser(String title, String message) {
        userNotifier.notify(title == null ? "jagt" : title, message);
        return "Notification sent";
    }

    private void markOutcome(String taskId, TaskStatus status, String message) {
        String id = stateService.canonicalTaskId(taskId);
        TaskStatus previous = stateService.task(id).map(TaskState::status).orElse(null);
        boolean updated = stateService.updateTask(id, t -> t.withStatus(status, message));
        // Never for a no-op (task gone) or a re-poll landing on the status the human already saw.
        if (updated && status != previous) {
            ping(id, status, message, stateService.task(id));
        }
    }

    private void ping(String taskId, TaskStatus status, String message, Optional<TaskState> task) {
        RoundState round = RoundState.of(message, task.map(t -> t.withStatus(status, message))
                .map(WorktreeFiles::draftedReplies).orElse(false));
        userNotifier.notify("jagt · " + taskId, Move.forTask(status, true, round).hint());
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
