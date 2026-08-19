package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The {@code stats} answer: what jagt's own model calls cost, then where the tasks' time has gone. Two
 * sections of one report rather than two commands, because both answer "is this arrangement working" and a
 * human asks that once.
 */
@Component
@RequiredArgsConstructor
public class StatsReport {

    private final StateService stateService;
    private final UsageStatsRenderer usage;
    private final CycleTimeRenderer cycleTimes;

    /** One read for both sections: a task retired between them would leave the two halves describing
     *  different sets of tasks. */
    public String render() {
        Map<String, TaskState> tasks = stateService.tasks();
        return usage.render(tasks) + "\n" + cycleTimes.render(tasks);
    }
}
