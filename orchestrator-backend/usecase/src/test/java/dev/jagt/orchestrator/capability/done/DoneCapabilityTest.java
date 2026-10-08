package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.FinishedTasks;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class DoneCapabilityTest {

    private final TaskRetirement retirement = mock(TaskRetirement.class);
    private final FinishedTasks finished = mock(FinishedTasks.class);
    private final FinishedArtifacts artifacts = mock(FinishedArtifacts.class);

    @Test
    void recordsAndKeepsTheArtifactsBeforeRetirementDropsWhatBothAreBuiltFrom() {
        new DoneCapability(retirement, finished, artifacts).run("ABC-1");

        InOrder order = inOrder(finished, artifacts, retirement);
        order.verify(finished).record("ABC-1");
        order.verify(artifacts).keep("ABC-1");
        order.verify(retirement).retire("ABC-1");
    }
}
