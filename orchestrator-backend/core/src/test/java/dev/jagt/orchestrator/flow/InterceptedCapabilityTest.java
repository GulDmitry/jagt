package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.CapabilityInterceptor;
import dev.jagt.orchestrator.port.TaskCapability;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterceptedCapabilityTest {

    private record Ships(List<String> log) implements TaskCapability {
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

    private record Announces(List<String> log, int order) implements CapabilityInterceptor {
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
    private final TaskStore tasks = mock(TaskStore.class);
    private final AgentPresence agents = mock(AgentPresence.class);

    private FlowEngine engineWith(List<CapabilityInterceptor> around) {
        when(tasks.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(tasks.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS).build()));
        return new FlowEngine(tasks, new Capabilities(List.of(new Ships(log)), around), agents);
    }

    @Test
    void runsTheWorkInsideEveryStepAnInstallPutAroundTheVerb() {
        FlowEngine engine = engineWith(List.of(new Announces(log, 2), new Announces(log, 1)));

        engine.run("ABC-1", TaskAction.SHIP);

        assertThat(log).containsExactly("before 1", "before 2", "shipped", "after 2", "after 1");
    }

    /** A step that refuses is a gate, so neither the work nor the transition may happen behind it. */
    @Test
    void stopsTheWorkAndTheTransitionWhenAStepAroundItRefuses() {
        FlowEngine engine = engineWith(List.of(new Refuses()));

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.SHIP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("our own check says no");
        assertThat(log).isEmpty();
        verify(tasks, never()).updateTask(eq("ABC-1"), any());
    }

    @Test
    void leavesAVerbNobodyWrappedExactlyAsItWas() {
        FlowEngine engine = engineWith(List.of());

        assertThat(engine.run("ABC-1", TaskAction.SHIP)).isEqualTo("ship ABC-1: done");
        assertThat(log).containsExactly("shipped");
    }
}
