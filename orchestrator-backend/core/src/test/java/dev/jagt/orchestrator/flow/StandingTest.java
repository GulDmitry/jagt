package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandingTest {

    @Test
    void staysTheSameWhereOnlyAPollWasWritten() {
        TaskState read = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

        TaskState polled = read.withLastPolledAt(5_000);

        assertThat(Standing.of(polled)).isEqualTo(Standing.of(read));
    }

    @Test
    void differsWhereTheTurnWasOpened() {
        TaskState read = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

        TaskState relayed = read.withTurn(read.turnOrNone().relayed("ship", "digest", "", 1_000));

        assertThat(Standing.of(relayed)).isNotEqualTo(Standing.of(read));
    }
}
