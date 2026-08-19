package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.SessionHost;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import dev.jagt.orchestrator.job.Job;
import org.springframework.stereotype.Service;

import java.time.Duration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects silently DEAD agents (token limit, pending prompt, crash) — not merely busy ones. A task
 * is only flagged when it is both silent on MCP (stale lastActiveTimestamp) AND its tmux window has
 * produced no output for the threshold: a working agent keeps printing (spinner, tokens, build logs)
 * even between MCP calls, so the window-activity check removes the false "unresponsive" alerts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchdogService implements Job {
    @Override
    public String id() {
        return "watchdog";
    }

    @Override
    public String describe() {
        return "alert when an agent has gone quiet in a status where it is supposed to be working";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }


    private final StateService stateService;
    private final Notifications notifications;
    private final OrchestratorProperties properties;
    private final SessionHost sessions;
    private final ConfigService configService;
    private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();

    /**
     * The statuses where the agent is EXPECTED to be doing something, so silence means death — NEW included,
     * for an agent that died before its first status update at all (spawn failure, auth prompt).
     *
     * <p>Every other status idles BY DESIGN and must stay unwatched, or the alert becomes noise. REVIEW_PENDING
     * and CI_FAILED are the arguable ones: right after a relayed brief the agent IS working, but telling
     * "relayed, unanswered" apart from "waiting for you" needs state jagt does not keep, and guessing would
     * fire alerts at a human who is simply taking their time.
     */
    static boolean watches(TaskStatus status) {
        return status == TaskStatus.NEW || status == TaskStatus.IN_PROGRESS || status == TaskStatus.SHIPPING;
    }

    @Override
    public void run() {
        long staleMs = properties.watchdog().staleAfter().toMillis();
        long now = System.currentTimeMillis();
        String session = sessions.sessionName(configService.load().viewer().tmuxSession());
        stateService.tasks().forEach((taskId, task) -> {
            if (!watches(task.status()) || now - task.lastActiveTimestamp() < staleMs) {
                lastAlertAt.remove(taskId);
                return;
            }
            long windowActivity = sessions.lastWindowActivityMillis(session, taskId);
            if (windowActivity > 0 && now - windowActivity < staleMs) {
                lastAlertAt.remove(taskId);
                return;
            }
            // Re-alert at most once per stale window to avoid notification spam.
            if (now - lastAlertAt.getOrDefault(taskId, 0L) < staleMs) {
                return;
            }
            lastAlertAt.put(taskId, now);
            long silentMinutes = (now - task.lastActiveTimestamp()) / 60_000;
            log.warn("Watchdog: task {} silent for {} min", taskId, silentMinutes);
            notifications.send(Notification.watchdog(taskId, "agent unresponsive",
                    "silent for " + silentMinutes + " min"));
        });
    }
}
