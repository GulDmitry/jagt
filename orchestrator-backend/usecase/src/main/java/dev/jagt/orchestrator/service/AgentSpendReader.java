package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.AgentSpend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What a sub-agent's own session cost, booked to its task — the work that spends most, and the only spend
 * nothing was metering.
 *
 * <p>Reading is bounded and repeatable: at most one window per report, from the mark that log carries, and the
 * mark is checked again inside the write. Two reports arriving together therefore book the window once — the
 * second sees the mark already moved and drops what it counted rather than adding it twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSpendReader {

    /** One report reads at most this much, so a log of any size costs a bounded read and the rest waits. */
    private static final long WINDOW = 8L * 1024 * 1024;

    private final StateService stateService;
    private final SessionLog sessionLog;

    /** Best-effort: a log that is gone, unreadable or in another shape costs a number, never a report. */
    public void charge(String taskId, Path log) {
        String name = log.toAbsolutePath().normalize().toString();
        long size;
        try {
            size = Files.size(log);
        } catch (IOException | RuntimeException gone) {
            return;
        }
        long from = stateService.task(taskId).map(task -> task.agentSpendOrNone().markFor(name)).orElse(0L);
        // A log SHORTER than its own mark was rewritten under jagt, and what of it was already counted cannot be
        // told. Its total stands and the mark follows the file: an under-count beats counting turns twice.
        if (size < from) {
            stateService.updateTask(taskId, task -> task.withAgentSpend(
                    task.agentSpendOrNone().plus(dev.jagt.orchestrator.task.TokenUsage.NONE, name, size)));
            return;
        }
        if (size == from) {
            return;
        }
        SessionLog.Spent spent = sessionLog.spent(log, from, Math.min(size - from, WINDOW));
        if (spent.upTo() <= from) {
            return;
        }
        stateService.updateTask(taskId, task -> {
            AgentSpend counted = task.agentSpendOrNone();
            return counted.markFor(name) == from
                    ? task.withAgentSpend(counted.plus(spent.usage(), name, spent.upTo()))
                    : task;
        });
    }
}
