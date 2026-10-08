package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.TaskStore;

import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

class FlowReportsTest {

    private final TaskStore stateService = mock(TaskStore.class);
    private final FlowReports reports = new FlowReports(stateService);

    @Test
    void letsATaskSayItIsWaitingOnTheHuman() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        reports.report("ABC-1", TaskStatus.REVIEW_PENDING, "widget fixed");

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()).status())
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

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEWED", "APPROVED"})
    void refusesASessionThatReportsItsOwnReviewVerdict(TaskStatus verdict) {
        assertThatThrownBy(() -> reports.report("ABC-1", verdict, "lgtm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jagt's to set");
        verifyNoInteractions(stateService);
    }

    @Test
    void landsAnApprovalTheHostsRoundReadConcluded() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        reports.read("ABC-1", TaskStatus.APPROVED, "approved");

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()).status())
                .isEqualTo(TaskStatus.APPROVED);
    }

    @Test
    void recordsAStartingAgentsLineWithoutTakingARevertedDeployOffTheRecord() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        reports.report("ABC-1", TaskStatus.IN_PROGRESS, "picking the task back up");

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        TaskState after = write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.REVERTED).build());
        assertThat(after.status()).isEqualTo(TaskStatus.REVERTED);
        assertThat(after.message()).isEqualTo("picking the task back up");
    }

    @Test
    void recordsTheRequestLinkInTheSameWriteAsTheStatusThatNeedsIt() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        reports.report("ABC-1", TaskStatus.CI_POLLING, "review request: http://host/1",
                (was, task) -> task.withMrUrl("http://host/1"));

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        TaskState after = write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build());
        assertThat(after.status()).isEqualTo(TaskStatus.CI_POLLING);
        assertThat(after.mrUrl()).isEqualTo("http://host/1");
    }
}
