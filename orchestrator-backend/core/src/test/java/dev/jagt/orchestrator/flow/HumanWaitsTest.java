package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.HumanWait;
import dev.jagt.orchestrator.task.HumanWait.Why;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn.Holder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HumanWaitsTest {

    @Test
    void opensAnIntervalWhenTheTurnPassesToTheHumanAndClosesItWhenItLeaves() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.QUESTION).build();

        TaskState opened = HumanWaits.noted(asking, Holder.HUMAN, 100);
        TaskState closed = HumanWaits.noted(opened.withReport(AgentReport.PLAIN), Holder.SESSION, 250);

        assertThat(opened.waits()).containsExactly(new HumanWait(100, 0, Why.QUESTION));
        assertThat(closed.waits()).containsExactly(new HumanWait(100, 250, Why.QUESTION));
    }

    @Test
    void keepsTheOpenIntervalWhileTheHumanStillHoldsTheTurnForTheSameReason() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.QUESTION)
                .waits(List.of(new HumanWait(100, 0, Why.QUESTION))).build();

        TaskState noted = HumanWaits.noted(asking, Holder.HUMAN, 900);

        assertThat(noted).isSameAs(asking);
    }

    @Test
    void splitsTheIntervalWhereAStopTakesOverTheReasonForWaiting() {
        TaskState stopped = TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
                .stop(new TaskStop(TaskStop.Trigger.MISS, 300, "no case"))
                .waits(List.of(new HumanWait(100, 0, Why.VERDICT))).build();

        TaskState noted = HumanWaits.noted(stopped, Holder.HUMAN, 300);

        assertThat(noted.waits()).containsExactly(new HumanWait(100, 300, Why.VERDICT), new HumanWait(300, 0, Why.STOP));
    }

    @Test
    void namesAPermissionPromptApartFromAQuestion() {
        TaskState prompt = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.PERMISSION).build();

        TaskState noted = HumanWaits.noted(prompt, Holder.HUMAN, 5);

        assertThat(noted.waits()).containsExactly(new HumanWait(5, 0, Why.PERMISSION));
    }

    @Test
    void opensNothingWhileAnotherHoldsTheTurn() {
        TaskState working = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

        TaskState noted = HumanWaits.noted(working, Holder.MASTER, 5);

        assertThat(noted).isSameAs(working);
    }

    @Test
    void keepsTheNewestFiftyIntervalsOnly() {
        List<HumanWait> fifty = java.util.stream.IntStream.rangeClosed(1, HumanWaits.MAX)
                .mapToObj(i -> new HumanWait(i, i + 1, Why.VERDICT)).toList();
        TaskState full = TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED).waits(fifty).build();

        TaskState noted = HumanWaits.noted(full, Holder.HUMAN, 999);

        assertThat(noted.waits()).hasSize(HumanWaits.MAX).doesNotContain(new HumanWait(1, 2, Why.VERDICT))
                .contains(new HumanWait(999, 0, Why.VERDICT));
    }

    @Test
    void endsAnIntervalStillOpenWhenTheTaskIsRetiredIntoItsFinishedRecord() {
        TaskState retired = TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .waits(List.of(new HumanWait(100, 0, Why.VERDICT))).build();

        FinishedTask finished = FinishedTask.of("ABC-42", retired, 700);

        assertThat(finished.waits()).containsExactly(new HumanWait(100, 700, Why.VERDICT));
    }
}
