package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.UserNotifier;
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
    private final UserNotifier userNotifier;
    private final OrchestratorProperties properties;
    private final TmuxService tmuxService;
    private final ConfigService configService;
    private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();

    /**
     * The statuses in which jagt EXPECTS the agent to be doing something, so silence means death:
     * NEW (died before its first status update — spawn failure, auth prompt), IN_PROGRESS, and SHIPPING
     * (the documented "stuck at SHIPPING, no request appears" failure, previously invisible to the watchdog —
     * recovery depended on the human noticing).
     *
     * <p>Every other status is idle BY DESIGN and must stay unwatched or the alert becomes noise:
     * REVIEW_PENDING / REVIEWED / APPROVED / CI_FAILED / DEPLOY_CONFLICT wait on the HUMAN (jagt's own
     * next-move for CI_FAILED is "your move: review"), CI_POLLING waits on the code host, DEPLOYED and DONE
     * are terminal. REVIEW_PENDING and CI_FAILED are the arguable ones: right after a relayed brief the agent
     * IS working. Telling "relayed, unanswered" apart from "waiting for you" needs state jagt does not keep,
     * and guessing would fire alerts at a human who is simply taking their time.
     */
    static boolean watches(TaskStatus status) {
        return status == TaskStatus.NEW || status == TaskStatus.IN_PROGRESS || status == TaskStatus.SHIPPING;
    }

    @Override
    public void run() {
        long staleMs = properties.watchdog().staleAfter().toMillis();
        long now = System.currentTimeMillis();
        String session = tmuxService.sessionName(configService.load().viewer().tmuxSession());
        stateService.tasks().forEach((taskId, task) -> {
            if (!watches(task.status()) || now - task.lastActiveTimestamp() < staleMs) {
                lastAlertAt.remove(taskId);
                return;
            }
            // Silent on MCP, but is it actually working? A busy agent keeps printing (spinner,
            // tokens, build logs) — recent tmux window output means it's alive, not hung.
            long windowActivity = tmuxService.lastWindowActivityMillis(session, taskId);
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
            userNotifier.notify("Orchestrator Alert",
                    "Agent " + taskId + " unresponsive (" + silentMinutes + " min)");
        });
    }
}
