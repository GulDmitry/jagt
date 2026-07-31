package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.UserNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects silently DEAD agents (token limit, pending prompt, crash) — not merely busy ones. A task
 * is only flagged when it is both silent on MCP (stale lastActiveTimestamp) AND its tmux window has
 * produced no output for the threshold: a working agent keeps printing (spinner, tokens, build logs)
 * even between MCP calls, so the window-activity check removes the false "unresponsive" alerts.
 */
@Service
public class WatchdogService {

    private static final Logger log = LoggerFactory.getLogger(WatchdogService.class);

    private final StateService stateService;
    private final UserNotifier userNotifier;
    private final OrchestratorProperties properties;
    private final TmuxService tmuxService;
    private final ConfigService configService;
    private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();

    public WatchdogService(StateService stateService, UserNotifier userNotifier, OrchestratorProperties properties,
                           TmuxService tmuxService, ConfigService configService) {
        this.stateService = stateService;
        this.userNotifier = userNotifier;
        this.properties = properties;
        this.tmuxService = tmuxService;
        this.configService = configService;
    }

    @Scheduled(fixedRate = 60_000)
    public void scan() {
        long staleMs = properties.watchdog().staleAfter().toMillis();
        long now = System.currentTimeMillis();
        String session = tmuxService.sessionName(configService.load().tmuxSession());
        stateService.tasks().forEach((taskId, task) -> {
            // NEW is watched too: an agent that dies before its first status update
            // (spawn failure, auth prompt) is exactly the silent death to catch.
            boolean active = task.status() == TaskStatus.IN_PROGRESS || task.status() == TaskStatus.NEW;
            if (!active || now - task.lastActiveTimestamp() < staleMs) {
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
