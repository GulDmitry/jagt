package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.Capabilities;
import dev.jagt.orchestrator.flow.capability.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class FlowEngineInterceptorTest {

  private final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  private static final class Ships implements TaskCapability {
    private final List<String> log;

    Ships(List<String> log) {
      this.log = log;
    }

    @Override
    public TaskAction action() {
      return TaskAction.SHIP;
    }

    @Override
    public Outcome run(String taskId) {
      log.add("shipped");
      return Outcome.ok("ship " + taskId + ": done", "review request: https://host/mr/1");
    }
  }

  private static final class Announces implements CapabilityInterceptor {
    private final List<String> log;
    private final int order;

    Announces(List<String> log, int order) {
      this.log = log;
      this.order = order;
    }

    @Override
    public int order() {
      return order;
    }

    @Override
    public TaskAction action() {
      return TaskAction.SHIP;
    }

    @Override
    public Outcome around(String taskId, Supplier<Outcome> work) {
      log.add("before " + order);
      Outcome outcome = work.get();
      log.add("after " + order);
      return outcome;
    }
  }

  private record Refuses() implements CapabilityInterceptor {
    @Override
    public TaskAction action() {
      return TaskAction.SHIP;
    }

    @Override
    public Outcome around(String taskId, Supplier<Outcome> work) {
      throw new IllegalStateException("our own check says no");
    }
  }

  private final List<String> log = new ArrayList<>();
  private final ReducingStore tasks = mock(ReducingStore.class);
  private final AgentPresence agents = mock(AgentPresence.class);
  private final FactSources facts = new FactSources(agents, ignored -> false);
  private final TaskState inProgress =
      TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS).build();

  @Test
  void runsTheWorkInsideEveryStepAnInstallPutAroundTheVerb() {
    when(tasks.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(tasks.task("ABC-1")).thenReturn(Optional.of(inProgress));
    FlowEngine engine =
        new FlowEngine(
            tasks,
            new Capabilities(
                List.of(new Ships(log)), List.of(new Announces(log, 2), new Announces(log, 1))),
            facts,
            Clock.systemUTC(),
            rules);

    engine.run("ABC-1", TaskAction.SHIP);

    assertThat(log).containsExactly("before 1", "before 2", "shipped", "after 2", "after 1");
  }

  @Test
  void stopsTheWorkAndTheTransitionWhenStepAroundItRefuses() {
    when(tasks.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(tasks.task("ABC-1")).thenReturn(Optional.of(inProgress));
    FlowEngine engine =
        new FlowEngine(
            tasks,
            new Capabilities(List.of(new Ships(log)), List.of(new Refuses())),
            facts,
            Clock.systemUTC(),
            rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.SHIP))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("our own check says no");
    assertThat(log).isEmpty();
    verify(tasks, never()).reduce(eq("ABC-1"), any());
  }

  @Test
  void leavesVerbNobodyWrappedExactlyAsItWas() {
    when(tasks.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(tasks.task("ABC-1")).thenReturn(Optional.of(inProgress));
    FlowEngine engine =
        new FlowEngine(
            tasks,
            new Capabilities(List.of(new Ships(log)), List.of()),
            facts,
            Clock.systemUTC(),
            rules);

    assertThat(engine.run("ABC-1", TaskAction.SHIP)).isEqualTo("ship ABC-1: done");
    assertThat(log).containsExactly("shipped");
  }
}
