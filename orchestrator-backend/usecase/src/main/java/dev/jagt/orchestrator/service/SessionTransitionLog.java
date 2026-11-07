package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.SessionPhase;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionTransitionLog {

  private final StateService stateService;
  private final Map<String, Seen> seen = new HashMap<>();

  private record Seen(SessionPhase phase, Session.@Nullable Window window) {

    static Seen of(TaskState task) {
      return new Seen(SessionPhase.of(task.session()), task.session().window());
    }

    Session.Window.@Nullable State windowState() {
      return window == null ? null : window.state();
    }
  }

  @PostConstruct
  void follow() {
    stateService.tasks().forEach((id, task) -> seen.put(id, Seen.of(task)));
    stateService.onChange(written -> observe(written.tasks()));
  }

  synchronized void observe(Map<String, TaskState> tasks) {
    tasks.forEach(
        (id, task) -> {
          Seen now = Seen.of(task);
          Seen before = seen.put(id, now);
          if (before != null) {
            report(id, before, now, task.session().halt());
          }
        });
    seen.keySet().retainAll(tasks.keySet());
  }

  private static void report(String id, Seen before, Seen now, Session.@Nullable Halt halt) {
    if (before.windowState() != now.windowState()) {
      log.atInfo()
          .setMessage("session window changed")
          .addKeyValue("task", id)
          .addKeyValue("from", Objects.toString(before.windowState(), "none"))
          .addKeyValue("to", Objects.toString(now.windowState(), "none"))
          .log();
    }
    if (before.phase() != SessionPhase.GONE && now.phase() == SessionPhase.GONE) {
      var gone = log.atInfo().setMessage("session gone").addKeyValue("task", id);
      if (halt != null && halt.cause() != null) {
        gone = gone.addKeyValue("cause", halt.cause());
      }
      gone.log();
    }
  }
}
