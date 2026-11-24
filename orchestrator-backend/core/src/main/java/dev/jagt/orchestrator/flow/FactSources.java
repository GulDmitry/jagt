package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.CurrentOrigin;
import dev.jagt.orchestrator.port.GitDistrust;
import dev.jagt.orchestrator.port.MergedRefresh;
import dev.jagt.orchestrator.port.codehost.RequestlessShip;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What a task's facts read from outside its stored state: its agent's window, its ship, and what a
 * press alone reads: the repository's trust, the merges, the findings awaiting an answer and who
 * pressed.
 */
public record FactSources(
    AgentPresence agents,
    RequestlessShip requestless,
    GitDistrust trust,
    MergedRefresh merged,
    CurrentOrigin origin,
    Function<String, Optional<String>> findings) {

  boolean agentLive(String taskId) {
    try {
      return agents.agentLive(taskId);
    } catch (AgentPresence.Unread unread) {
      throw Refusal.byState(
          "Cannot tell whether the session runs, its window was not read: " + unread.getMessage());
    }
  }

  boolean shippedWithoutRequest(TaskState task) {
    return requestless.stands(task);
  }

  /** Read once, on the first ask: a press that does not reach the gate reads nothing. */
  Supplier<Optional<String>> distrust(String taskId) {
    return once(() -> trust.distrusted(taskId));
  }

  Supplier<Optional<String>> findingsOpen(String taskId) {
    return once(() -> findings.apply(taskId));
  }

  BooleanSupplier mergedNow(String taskId) {
    Supplier<Boolean> read = once(() -> merged.refresh(taskId));
    return read::get;
  }

  @Nullable ActionOrigin pressedBy() {
    return origin.current();
  }

  private static <T> Supplier<T> once(Supplier<T> read) {
    AtomicReference<@Nullable T> held = new AtomicReference<>();
    return () -> {
      T value = held.get();
      if (value == null) {
        value = read.get();
        held.set(value);
      }
      return value;
    };
  }
}
