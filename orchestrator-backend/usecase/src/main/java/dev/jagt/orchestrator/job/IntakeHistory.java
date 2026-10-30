package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.FinishedTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The keys intake may not take again: the tasks jagt holds, the ones it finished, and the ones it turned away.
 * A turned-away key is kept beside state.json — re-reading an item nothing can route costs the same as reading
 * one that can, every poll, forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntakeHistory {

    private final StateService stateService;
    private final FinishedTasks finished;
    private final Notifications notifications;
    private final OrchestratorPaths paths;
    private final Set<String> turnedAway = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void follow() {
        turnedAway.addAll(kept());
    }

    public Set<String> held() {
        Set<String> keys = new HashSet<>(stateService.tasks().keySet());
        finished.all().stream().map(FinishedTask::id).forEach(keys::add);
        keys.addAll(turnedAway);
        return keys;
    }

    /** Said once per key: a human reads it, and a second copy every poll is not a second fact. */
    public void turnAway(String key, String cause) {
        if (!turnedAway.add(key)) {
            return;
        }
        keep();
        log.atWarn().setMessage("intake turned an item away")
                .addKeyValue("ref", key)
                .addKeyValue("cause", cause)
                .log();
        notifications.send(Notification.housekeeping("intake cannot start " + key,
                cause + " — start it by hand with `do " + key + " <project>`"));
    }

    private List<String> kept() {
        try {
            return Files.isRegularFile(paths.intakeTurnedAway()) ? Files.readAllLines(paths.intakeTurnedAway()) : List.of();
        } catch (IOException e) {
            log.atWarn().setMessage("intake turned-away keys unread").addKeyValue("file", paths.intakeTurnedAway())
                    .addKeyValue("cause", e.toString()).addKeyValue("effect", "they are told again").log();
            return List.of();
        }
    }

    private void keep() {
        try {
            Files.writeString(paths.intakeTurnedAway(), String.join("\n", turnedAway) + "\n");
        } catch (IOException e) {
            log.atWarn().setMessage("intake turned-away keys not kept").addKeyValue("file", paths.intakeTurnedAway())
                    .addKeyValue("cause", e.toString()).addKeyValue("effect", "a restart tells them again").log();
        }
    }
}
