package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoneCapabilityTest {

    private final TaskRetirement retirement = mock(TaskRetirement.class);
    private final FinishedTasks finished = mock(FinishedTasks.class);
    private final FinishedArtifacts artifacts = mock(FinishedArtifacts.class);

    @Test
    void recordsAndKeepsTheArtifactsBeforeRetirementDropsWhatBothAreBuiltFrom() {
        when(retirement.retire("ABC-1")).thenReturn("Task ABC-1 removed");

        Outcome outcome = new DoneCapability(retirement, finished, artifacts).run("ABC-1");

        assertThat(outcome.message()).isEqualTo("Task ABC-1 removed");
        InOrder order = inOrder(finished, artifacts, retirement);
        order.verify(finished).record("ABC-1");
        order.verify(artifacts).keep("ABC-1");
        order.verify(retirement).retire("ABC-1");
    }

    @Test
    void doneVerbRetiresTheTask() {
        assertThat(new DoneCapability(retirement, finished, artifacts).action()).isEqualTo(TaskAction.DONE);
    }
}
