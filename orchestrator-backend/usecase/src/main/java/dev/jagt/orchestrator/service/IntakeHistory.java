package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.FinishedTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The keys intake may not take again: the tasks jagt holds, the ones it finished, and the ones it turned away.
 * A turned-away key is remembered for the life of the process — re-reading an item nothing can route costs the
 * same as reading one that can, every poll, forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntakeHistory {

    private final StateService stateService;
    private final FinishedTasks finished;
    private final Notifications notifications;
    private final Set<String> turnedAway = ConcurrentHashMap.newKeySet();

    public Set<String> held() {
        Set<String> keys = new HashSet<>(stateService.tasks().keySet());
        finished.all().stream().map(FinishedTask::id).forEach(keys::add);
        keys.addAll(turnedAway);
        return keys;
    }

    /** Said once per key and per process: a human reads it, and a second copy every poll is not a second fact. */
    public void turnAway(String key, String cause) {
        if (!turnedAway.add(key)) {
            return;
        }
        log.atWarn().setMessage("intake turned an item away")
                .addKeyValue("ref", key)
                .addKeyValue("cause", cause)
                .log();
        notifications.send(Notification.housekeeping("intake cannot start " + key,
                cause + " — start it by hand with `do " + key + " <project>`"));
    }
}
