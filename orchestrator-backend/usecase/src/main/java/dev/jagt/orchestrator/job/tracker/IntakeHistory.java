package dev.jagt.orchestrator.job.tracker;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.channel.Notification;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.task.FinishedTasks;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The keys intake may not take again: the tasks jagt holds and the ones it finished. A turned-away
 * key is tried again every poll, since its cause may be fixed; only its telling is kept beside
 * state.json.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntakeHistory {

  private final StateService stateService;
  private final FinishedTasks finished;
  private final Notifications notifications;
  private final OrchestratorPaths paths;
  private final Set<String> told = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean loaded = new AtomicBoolean();

  public Set<String> held() {
    Set<String> keys = new HashSet<>(stateService.tasks().keySet());
    finished.all().stream().map(task -> task.identity().id()).forEach(keys::add);
    return keys;
  }

  /** Said once per key: a human reads it, and a second copy every poll is not a second fact. */
  public void turnAway(String key, String cause) {
    if (loaded.compareAndSet(false, true)) {
      told.addAll(kept());
    }
    if (!told.add(key)) {
      return;
    }
    keep();
    log.atWarn()
        .setMessage("intake turned an item away")
        .addKeyValue("ref", key)
        .addKeyValue("cause", cause)
        .log();
    notifications.send(
        Notification.housekeeping(
            Point.HOUSEKEEPING.id(),
            "intake cannot start " + key,
            cause + " — start it with the board's `do " + key + " <project>`"));
  }

  private List<String> kept() {
    try {
      return Files.isRegularFile(paths.intakeTurnedAway())
          ? Files.readAllLines(paths.intakeTurnedAway())
          : List.of();
    } catch (IOException e) {
      log.atWarn()
          .setMessage("intake turned-away keys unread")
          .addKeyValue("file", paths.intakeTurnedAway())
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "they are told again")
          .log();
      return List.of();
    }
  }

  private void keep() {
    try {
      Files.writeString(paths.intakeTurnedAway(), String.join("\n", told) + "\n");
    } catch (IOException e) {
      log.atWarn()
          .setMessage("intake turned-away keys not kept")
          .addKeyValue("file", paths.intakeTurnedAway())
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "a restart tells them again")
          .log();
    }
  }
}
