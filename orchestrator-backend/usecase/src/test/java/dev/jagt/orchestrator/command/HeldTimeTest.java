package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class HeldTimeTest {

    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1_700_000_000_000L;

    @Test
    void doesNotChargeTheGapAfterTheOpeningStepToAnyStatus() {
        List<StatusChange> history = new ArrayList<>(List.of(new StatusChange(TaskStatus.NEW, NOW - 100 * HOUR, null)));
        IntStream.range(1, 50).forEach(step -> history.add(new StatusChange(TaskStatus.CI_POLLING, NOW - (50 - step) * HOUR, null)));

        long held = HeldTime.byOwner(history, NOW).values().stream().mapToLong(Long::longValue).sum();

        assertThat(held).isEqualTo(49 * HOUR);
        assertThat(HeldTime.byOwner(history, NOW).get(Owner.CI)).isEqualTo(49 * HOUR);
    }
}
