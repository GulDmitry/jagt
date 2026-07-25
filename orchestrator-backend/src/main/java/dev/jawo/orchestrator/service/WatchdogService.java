package dev.jawo.orchestrator.service;

import dev.jawo.orchestrator.config.OrchestratorProperties;
import dev.jawo.orchestrator.model.TaskStatus;
import dev.jawo.orchestrator.platform.UserNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects silently dead agents: a task stuck IN_PROGRESS whose
 * lastActiveTimestamp is older than the configured threshold means the CLI
 * session hung (token limit, pending confirmation prompt, crash).
 */
@Service
public class WatchdogService {

    private static final Logger log = LoggerFactory.getLogger(WatchdogService.class);

    private final StateService stateService;
    private final UserNotifier userNotifier;
    private final OrchestratorProperties properties;
    private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();

    public WatchdogService(StateService stateService, UserNotifier userNotifier, OrchestratorProperties properties) {
        this.stateService = stateService;
        this.userNotifier = userNotifier;
        this.properties = properties;
    }

    @Scheduled(fixedRate = 60_000)
    public void scan() {
        long staleMs = properties.watchdog().staleAfter().toMillis();
        long now = System.currentTimeMillis();
        stateService.tasks().forEach((taskId, task) -> {
            // NEW is watched too: an agent that dies before its first status update
            // (spawn failure, auth prompt) is exactly the silent death to catch.
            boolean active = task.status() == TaskStatus.IN_PROGRESS || task.status() == TaskStatus.NEW;
            if (!active || now - task.lastActiveTimestamp() < staleMs) {
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
