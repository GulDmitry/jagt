package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.AgentSpend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * What a sub-agent's own session cost, booked to its task. Reading is bounded and repeatable: at most one window per
 * report, from the mark that log carries, and the mark is checked again inside the write, so two reports arriving
 * together book the window once.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSpendReader {

    private final StateService stateService;
    private final SessionLog sessionLog;

    /** Best-effort: a log that is gone, unreadable or in another shape costs a number, never a report. */
    public void charge(String taskId, Path log) {
        String name = log.toAbsolutePath().normalize().toString();
        long from = stateService.task(taskId).map(task -> task.agentSpendOrNone().markFor(name)).orElse(0L);
        LogSpend.since(sessionLog, log, from).ifPresent(advance -> stateService.updateTask(taskId, task -> {
            AgentSpend counted = task.agentSpendOrNone();
            // Checked again inside the write: two reports arriving together book the window once.
            return counted.markFor(name) == from
                    ? task.withAgentSpend(counted.plus(advance.usage(), name, advance.mark()))
                    : task;
        }));
    }
}
