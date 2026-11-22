package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.capability.Capabilities;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.CurrentOrigin;
import dev.jagt.orchestrator.port.GitDistrust;
import dev.jagt.orchestrator.port.MergedRefresh;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.With;

/** An engine whose one capability records that it ran, and what a press reads. */
@With
public record TestPressEngineBuilder(
    TaskAction action,
    GitDistrust trust,
    MergedRefresh merged,
    CurrentOrigin origin,
    List<String> ran) {

  private static final class Does implements TaskCapability {
    private final TaskAction action;
    private final List<String> ran;

    Does(TaskAction action, List<String> ran) {
      this.action = action;
      this.ran = ran;
    }

    @Override
    public TaskAction action() {
      return action;
    }

    @Override
    public Outcome run(String taskId) {
      ran.add(action.id());
      return Outcome.ok(action.id() + " " + taskId + ": done");
    }
  }

  public static TestPressEngineBuilder press(List<String> ran) {
    return new TestPressEngineBuilder(
        TaskAction.SHIP, id -> Optional.empty(), id -> false, () -> ActionOrigin.BOARD, ran);
  }

  public FlowEngine build(ReducingStore tasks, AgentPresence agents) {
    return new FlowEngine(
        tasks,
        new Capabilities(List.of(new Does(action, ran)), List.of()),
        new FactSources(agents, ignored -> false, trust, merged, origin),
        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        new FlowRules(new TaskChart(Composition.builtIn())));
  }
}
