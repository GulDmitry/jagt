package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskAdmissionTest {

    private static Map<String, TaskState> tasks(TaskStatus... statuses) {
        Map<String, TaskState> tasks = new java.util.LinkedHashMap<>();
        for (int i = 0; i < statuses.length; i++) {
            tasks.put("ABC-" + (i + 1), TaskState.builder("proj", "/wt/" + i, statuses[i])
                    .alias("a" + (i + 1)).build());
        }
        return tasks;
    }

    @Test
    void refusesANewTaskWhenEverySlotIsTakenAndSaysWhatFreesOne() {
        assertThatThrownBy(() -> TaskAdmission.requireSlot("ABC-9", 2,
                tasks(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot start ABC-9")
                .hasMessageContaining("all 2 task slots are in use")
                .hasMessageContaining("a1 ABC-1, a2 ABC-2")
                .hasMessageContaining("`done`")
                .hasMessageContaining("agent.maxConcurrentTasks");
    }

    @Test
    void admitsWhileASlotIsFree() {
        assertThatCode(() -> TaskAdmission.requireSlot("ABC-9", 2, tasks(TaskStatus.IN_PROGRESS)))
                .doesNotThrowAnyException();
    }

    /**
     * A task keeps its slot until `done` deletes the worktree — a DEPLOYED task still owns 1-2 GB of language
     * server and a checkout, so counting only the ones with a live agent would over-admit.
     */
    @Test
    void countsATaskThatIsFinishedButNotYetRemoved() {
        assertThatThrownBy(() -> TaskAdmission.requireSlot("ABC-9", 1, tasks(TaskStatus.DEPLOYED)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesNothingWhenTheCapIsZeroOrNegative() {
        Map<String, TaskState> full = tasks(TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS,
                TaskStatus.IN_PROGRESS);
        assertThatCode(() -> TaskAdmission.requireSlot("ABC-9", 0, full)).doesNotThrowAnyException();
        assertThatCode(() -> TaskAdmission.requireSlot("ABC-9", -1, full)).doesNotThrowAnyException();
    }
}
