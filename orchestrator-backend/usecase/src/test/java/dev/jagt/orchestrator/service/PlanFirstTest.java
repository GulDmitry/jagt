package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlanFirstTest {

    @ParameterizedTest
    @ValueSource(strings = {"/wt/src/Foo.java", "/wt/task_context.md", "/wt/sub/plan.md", "/elsewhere/plan.md"})
    void refusesAWriteToAnythingButThePlanningFilesWhileThePlanIsNotApproved(String file) {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).planGate(PlanGate.REQUIRED).build();

        assertThat(PlanFirst.refusal(task, file)).hasValueSatisfying(reason -> assertThat(reason)
                .startsWith("jagt refuses").contains("plan first", "plan.md"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/wt/plan.md", "/wt/task_visual.md", "/wt/task_notes.md", "plan.md", "/wt/x/../plan.md"})
    void letsTheFilesThatMakeThePlanThrough(String file) {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).planGate(PlanGate.CLOSED).build();

        assertThat(PlanFirst.refusal(task, file)).isEmpty();
    }

    @Test
    void letsEveryWriteThroughOnceThePlanIsApproved() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).planGate(PlanGate.OPEN).build();

        assertThat(PlanFirst.refusal(task, "/wt/src/Foo.java")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = PlanGate.class, names = {"CLOSED", "REQUIRED"})
    void refusesAWriteWhicheverWayTheGateWasClosed(PlanGate gate) {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).planGate(gate).build();

        assertThat(PlanFirst.refusal(task, "/wt/src/Foo.java")).isPresent();
    }
}
