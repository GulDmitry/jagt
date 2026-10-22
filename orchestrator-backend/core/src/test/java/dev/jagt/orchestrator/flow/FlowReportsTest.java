package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.TaskStore;

import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowReportsTest {

    private final TaskStore stateService = mock(TaskStore.class);
    private final FlowReports reports = new FlowReports(stateService, Clock.systemUTC());

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
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEWED", "APPROVED"})
    void refusesASessionThatReportsItsOwnReviewVerdict(TaskStatus verdict) {
        assertThatThrownBy(() -> reports.report("ABC-1", verdict, "lgtm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jagt's to set");
    }

    @Test
    void countsAReportTheTasksStatusForbidsOnTheTask() {
        AtomicReference<TaskState> written = new AtomicReference<>();
        when(stateService.updateTask(eq("ABC-1"), any())).thenAnswer(call -> {
            UnaryOperator<TaskState> write = call.getArgument(1);
            written.set(write.apply(TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build()));
            return true;
        });

        assertThatThrownBy(() -> reports.report("ABC-1", TaskStatus.PLAN_PENDING, "a plan"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(written.get().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
        assertThat(written.get().refusals()).containsExactly(Map.entry(Guard.STATUS_REPORT, 1));
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

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVERTED", "DEPLOY_CONFLICT"})
    void keepsTheLineOfAStatusAHumanHoldsWhenItsSessionReports(TaskStatus held) {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        reports.report("ABC-1", TaskStatus.IN_PROGRESS, "no changes: clean worktree");

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        TaskState after = write.getValue().apply(TaskState.builder("proj", "/wt", held)
                .message("deploy ABC-1: conflict in Foo.java").build());
        assertThat(after.status()).isEqualTo(held);
        assertThat(after.message()).isEqualTo("deploy ABC-1: conflict in Foo.java");
    }

    @Test
    void putsAQuestionOnTheCardOfAStatusAHumanHolds() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        reports.report("ABC-1", TaskStatus.IN_PROGRESS, "awaiting: keep both sides of Foo.java?");

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        TaskState after = write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .message("deploy ABC-1: conflict in Foo.java").build());
        assertThat(after.message()).isEqualTo("awaiting: keep both sides of Foo.java?");
    }

    @Test
    void leavesTheLineToTheHeldStatusOnceItsQuestionIsBehind() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        reports.report("ABC-1", TaskStatus.IN_PROGRESS, "resolving the merge");

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        TaskState after = write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .message("awaiting: keep both sides of Foo.java?").build());
        assertThat(after.message()).isNull();
    }

    @Test
    void recordsTheRequestLinkInTheSameWriteAsTheStatusThatNeedsIt() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(true);

        reports.report("ABC-1", TaskStatus.CI_POLLING, "review request: http://host/1",
                (was, task) -> task.withMrUrl("http://host/1", 2_000));

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        TaskState after = write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build());
        assertThat(after.status()).isEqualTo(TaskStatus.CI_POLLING);
        assertThat(after.mrUrl()).isEqualTo("http://host/1");
    }

    @Test
    void answersTrueAndTheStatusItFoundAndLeftWhenTheReportWasWritten() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenAnswer(call -> {
            UnaryOperator<TaskState> write = call.getArgument(1);
            write.apply(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
            return true;
        });

        boolean accepted = reports.report("ABC-1", TaskStatus.REVIEW_PENDING, "widget fixed");
        var landed = reports.report("ABC-1", TaskStatus.REVIEW_PENDING, "widget fixed", (was, task) -> task);

        assertThat(accepted).isTrue();
        assertThat(landed).contains(new FlowReports.Landed(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING));
    }

    @Test
    void answersFalseWhenTheTaskWasNotThereToWriteTheReportOn() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenReturn(false);

        assertThat(reports.report("ABC-1", TaskStatus.REVIEW_PENDING, "widget fixed")).isFalse();
    }

    @Test
    void landsAHandBackAtVerifyingWhileTheVerificationIsOwed() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenAnswer(call -> {
            UnaryOperator<TaskState> write = call.getArgument(1);
            write.apply(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
            return true;
        });

        var landed = reports.report("ABC-1", TaskStatus.REVIEW_PENDING, "widget fixed", (was, task) -> task,
                task -> true);

        assertThat(landed).contains(new FlowReports.Landed(TaskStatus.IN_PROGRESS, TaskStatus.VERIFYING));
    }

    @Test
    void tellsTheSessionWhyItsReportWasRefusedInTheStatusItIsIn() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenAnswer(call -> {
            UnaryOperator<TaskState> write = call.getArgument(1);
            write.apply(TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
            return true;
        });

        assertThatThrownBy(() -> reports.report("ABC-1", TaskStatus.PLAN_PENDING, "a plan"))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("already REVIEW_PENDING");
    }

    @Test
    void answersWhatARoundReadFoundAndLeftWhenItMovedTheTask() {
        when(stateService.updateTask(eq("ABC-1"), any())).thenAnswer(call -> {
            UnaryOperator<TaskState> write = call.getArgument(1);
            write.apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
            return true;
        });

        var landed = reports.read("ABC-1", TaskStatus.APPROVED, "approved");

        assertThat(landed).contains(new FlowReports.Landed(TaskStatus.CI_POLLING, TaskStatus.APPROVED));
    }

    @Test
    void writesNothingWhenARoundReadFindsTheTaskWhereItAlreadyIs() {
        AtomicReference<TaskState> written = new AtomicReference<>();
        TaskState before = TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build();
        when(stateService.updateTask(eq("ABC-1"), any())).thenAnswer(call -> {
            UnaryOperator<TaskState> write = call.getArgument(1);
            written.set(write.apply(before));
            return true;
        });

        reports.read("ABC-1", TaskStatus.APPROVED, "approved");

        assertThat(written.get()).isSameAs(before);
    }
}
