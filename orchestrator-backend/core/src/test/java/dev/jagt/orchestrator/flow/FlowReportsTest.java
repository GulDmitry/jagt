package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.TaskStore;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The second door into the machine: what a task may say about itself, and what only jagt may set. */
class FlowReportsTest {

    private final TaskStore stateService = mock(TaskStore.class);
    private final FlowReports reports = new FlowReports(stateService);

    @Test
    void letsATaskSayItIsWaitingOnTheHuman() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        assertThat(reports.report("ABC-1", TaskStatus.REVIEW_PENDING, "widget fixed")).isTrue();
        assertThat(written("ABC-1").apply(current(TaskStatus.IN_PROGRESS)).status())
                .isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void refusesToLetATaskTalkItselfOntoASharedBranch() {
        assertThatThrownBy(() -> reports.report("ABC-1", TaskStatus.DEPLOYED, "merged"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEPLOYED")
                .hasMessageContaining("jagt's to set");
        verifyNoInteractions(stateService);
    }

    /** A CI_POLLING with no request link is a lie on the dashboard, so the link cannot land in a later write. */
    @Test
    void recordsTheRequestLinkInTheSameWriteAsTheStatusThatNeedsIt() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        reports.report("ABC-1", TaskStatus.CI_POLLING, "review request: http://host/1",
                task -> task.withMrUrl("http://host/1"));

        TaskState after = written("ABC-1").apply(current(TaskStatus.SHIPPING));
        assertThat(after.status()).isEqualTo(TaskStatus.CI_POLLING);
        assertThat(after.mrUrl()).isEqualTo("http://host/1");
    }

    private static TaskState current(TaskStatus status) {
        return TaskState.builder("proj", "/wt", status).alias("a1").build();
    }

    private UnaryOperator<TaskState> written(String taskId) {
        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq(taskId), write.capture());
        return write.getValue();
    }
}
