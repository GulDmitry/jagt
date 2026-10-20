package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsReportTest {

    private final StateService stateService = mock(StateService.class);
    private final UsageStatsRenderer usage = mock(UsageStatsRenderer.class);
    private final CycleTimeRenderer cycleTimes = mock(CycleTimeRenderer.class);

    @Test
    void joinsTheSpendAndTheCycleTimeOfTheSameTasksIntoOneReport() {
        Map<String, TaskState> tasks = Map.of();
        when(stateService.tasks()).thenReturn(tasks);
        when(usage.render(tasks)).thenReturn("spend");
        when(cycleTimes.render(tasks)).thenReturn("cycle");

        String report = new StatsReport(stateService, usage, cycleTimes).render();

        assertThat(report).isEqualTo("spend\ncycle");
    }
}
